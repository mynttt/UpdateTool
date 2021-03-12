package updatetool.imdb;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.sqlite.SQLiteException;
import org.tinylog.Logger;
import com.google.common.collect.Lists;
import updatetool.api.AgentResolvementStrategy;
import updatetool.api.ExportedRating;
import updatetool.api.Pipeline;
import updatetool.common.Capabilities;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.common.externalapis.TmdbApiV3;
import updatetool.common.externalapis.TvdbApiV3;
import updatetool.common.externalapis.TvdbApiV4;
import updatetool.common.ErrorReports;
import updatetool.common.KeyValueStore;
import updatetool.common.SqliteDatabaseProvider;
import updatetool.common.Utility;
import updatetool.exceptions.ApiCallFailedException;
import updatetool.exceptions.DatabaseLockedException;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbRatingDatasetFactory.ImdbRatingDataset;
import updatetool.imdb.resolvement.DefaultResolvement;
import updatetool.imdb.resolvement.ImdbResolvement;
import updatetool.imdb.resolvement.NewPlexAgentToImdbResolvement;
import updatetool.imdb.resolvement.TmdbToImdbResolvement;
import updatetool.imdb.resolvement.TvdbToImdbResolvement;

public class ImdbPipeline extends Pipeline<ImdbJob> {
    private static final Pattern RESOLVEMENT = Pattern.compile(
                "(?<IMDB>agents.imdb:\\/\\/tt)"
                + "|(?<TMDB>agents.themoviedb:\\/\\/)"
                + "|(?<NPMA>plex:\\/\\/movie\\/)"
                + "|(?<NPSA>plex:\\/\\/(episode|season|show)\\/)"
                + "|(?<TVDB>agents.thetvdb:\\/\\/)"
            );

    private static final int LIST_PARTITIONS = 16;
    private static final int RETRY_N_SECONDS_IF_DB_LOCKED = 20;
    private static final int ABORT_DB_LOCK_WAITING_AFTER_N_RETRIES = 500;

    private final ImdbLibraryMetadata metadata;
    private final ExecutorService service;
    private final ImdbRatingDataset dataset;
    private final ImdbPipelineConfiguration configuration;
    private final HashMap<String, AgentResolvementStrategy<ImdbMetadataResult>> resolveMovies = new HashMap<>(), resolveSeries = new HashMap<>();
    private final EnumMap<LibraryType, HashMap<String, AgentResolvementStrategy<ImdbMetadataResult>>> resolvers = new EnumMap<>(LibraryType.class);
    public final AgentResolvementStrategy<ImdbMetadataResult> resolveDefault = new DefaultResolvement();

    public static class ImdbPipelineConfiguration {
        private final EnumSet<Capabilities> capabilities;
        public final String tmdbApiKey, tvdbApiKey, dbLocation;
        public final Path metadataRoot;
        public final boolean isTvdbV4;
        
        public ImdbPipelineConfiguration(String tmdbApiKey, String tvdbApiKey, Path metadataRoot, String dbLocation, EnumSet<Capabilities> capabilities) {
            this.tmdbApiKey = tmdbApiKey;
            this.tvdbApiKey = tvdbApiKey;
            this.metadataRoot = metadataRoot;
            this.dbLocation = dbLocation;
            this.capabilities =  capabilities;
            this.isTvdbV4 = resolveTvdb() ? tvdbApiKey.length() != 32 : false;
        }

        public boolean resolveTmdb() {
            return capabilities.contains(Capabilities.TMDB);
        }
        
        public boolean resolveTvdb() {
            return capabilities.contains(Capabilities.TVDB);
        }
    }
    
    public ImdbPipeline(ImdbLibraryMetadata metadata, ExecutorService service, Map<String, KeyValueStore> caches, ImdbPipelineConfiguration configuration, ImdbRatingDataset dataset) throws ApiCallFailedException {
        this.service = service;
        this.metadata = metadata;
        this.configuration = configuration;
        this.dataset = dataset;
        
        var tmdbResolver = configuration.resolveTmdb() ? new TmdbToImdbResolvement(new TmdbApiV3(configuration.tmdbApiKey,caches.get("tmdb-series"), caches.get("tmdb"), caches.get("tmdb-series-blacklist"), caches.get("tmdb-blacklist"))) : resolveDefault;
        var tvdbResolver = configuration.resolveTvdb() ? new TvdbToImdbResolvement(configuration.isTvdbV4 
                ? new TvdbApiV4(configuration.tvdbApiKey, caches.get("tvdb"), caches.get("tvdb-blacklist"), caches.get("tvdb-movie"), caches.get("tvdb-movie-blacklist"), caches.get("tvdb-legacy-mapping"))
                : new TvdbApiV3(configuration.tvdbApiKey, caches.get("tvdb"), caches.get("tvdb-blacklist"), caches.get("tvdb-movie"), caches.get("tvdb-movie-blacklist"))) 
                : resolveDefault;
        
        TmdbToImdbResolvement r1 = tmdbResolver == resolveDefault ? null : (TmdbToImdbResolvement) tmdbResolver;
        TvdbToImdbResolvement r2 = tvdbResolver == resolveDefault ? null : (TvdbToImdbResolvement) tvdbResolver;
        var newAgentResolver = new NewPlexAgentToImdbResolvement(caches.get("new-agent-mapping"), r1, r2);
        
        resolveMovies.put("IMDB", new ImdbResolvement());
        resolveMovies.put("TMDB", tmdbResolver);
        resolveMovies.put("NPMA", newAgentResolver);
        
        resolveSeries.put("TVDB", tvdbResolver);
        resolveSeries.put("TMDB", tmdbResolver);
        resolveSeries.put("IMDB", new ImdbResolvement());
        resolveSeries.put("NPSA", newAgentResolver);
        
        resolvers.put(LibraryType.MOVIE, resolveMovies);
        resolvers.put(LibraryType.SERIES, resolveSeries);
    }

    @Override
    public void analyseDatabase(ImdbJob job) throws Exception {
        Logger.info("Resolving IMDB identifiers for items. Only warnings and errors will show up...");
        Logger.info("Items that show up here will not be processed by further stages of the pipeline.");
        HashMap<String, AgentResolvementStrategy<ImdbMetadataResult>> resolve = resolvers.get(job.libraryType);
        Objects.requireNonNullElse(resolve, "No resolver registered for library type: " + job.libraryType);
        List<CompletableFuture<Void>> resolverTasks = new ArrayList<>();
        ConcurrentLinkedDeque<ImdbMetadataResult> resolved = new ConcurrentLinkedDeque<>();
        var items = metadata.request(job.uuid);
        for(var item : items) {
            var matcher = RESOLVEMENT.matcher(item.guid);
            if(matcher.find()) {
                for(var entry : resolve.entrySet()) {
                    if(matcher.group(entry.getKey()) == null)
                        continue;
                    resolverTasks.add(CompletableFuture.supplyAsync(() -> entry.getValue().resolve(item), service)
                            .thenAccept(b -> { if(b) resolved.add(item); }));
                    break;
                }
            } else {
                resolveDefault.resolve(item);
            }
        }
        
        resolverTasks.stream().forEach(CompletableFuture::join);
        
        int resolvedSize = resolved.size();
        int itemsSize = items.size();
        
        Logger.info("Filtered " + (itemsSize-resolvedSize) + " invalid item(s).");
        job.stage = PipelineStage.ANALYSED_DB;
        job.items = new ArrayList<>(resolved);
    }

    @Override
    public void accumulateMetadata(ImdbJob job) throws Exception {
        // Accumulation not necessary anymore since we can use the complete IMDB dataset here, thus skipping this step
        job.stage = PipelineStage.ACCUMULATED_META;
    }

    @Override
    public void transformMetadata(ImdbJob job) throws Exception {
        var map = new HashMap<ImdbMetadataResult, ExportedRating>();
        job.items.forEach(i -> map.put(i, dataset.getRatingFor(ImdbTransformer.clean(i.imdbId))));
        var noUpdate = map.entrySet().stream().filter(Predicate.not(ImdbTransformer::needsUpdate)).collect(Collectors.toSet());
        if(!noUpdate.isEmpty()) {
            Logger.info(noUpdate.size() + " item(s) need no update.");
            for(var item : noUpdate) {
                map.remove(item.getKey());
                job.items.remove(item.getKey());
            }
        }
        Logger.info("Transforming " + map.size() + " item(s)");
        map.entrySet().stream().forEach(ImdbTransformer::updateMetadata);
        Logger.info("Transformed entries for " + map.size() + " items(s).");
        job.stage = PipelineStage.TRANSFORMED_META;
    }

    @Override
    public void updateDatabase(ImdbJob job) throws Exception {
        if(job.items.isEmpty()) {
            Logger.info("Nothing to update. Skipping...");
            job.stage = PipelineStage.DB_UPDATED;
            return;
        }
        Logger.info("Updating " + job.items.size() + " via batch request...");
        int counter = 0;
        try(var connection = new SqliteDatabaseProvider(configuration.dbLocation)) {
            var db = new ImdbDatabaseSupport(connection);
            while(true) {
                if(counter++==(ABORT_DB_LOCK_WAITING_AFTER_N_RETRIES-1))
                    throw new DatabaseLockedException("Plex database is currently locked. After " + ABORT_DB_LOCK_WAITING_AFTER_N_RETRIES + " attempt(s) every " + RETRY_N_SECONDS_IF_DB_LOCKED + " second(s) of accessing an unlocked database this tool is destined to halt execution to prevent endless loops. Either stop Plex to run the tool or start the tool again and hope Plex has removed the lock.");
                try {
                    db.requestBatchUpdateOf(job.items);
                    break;
                } catch(SQLiteException e) {
                    if(!e.getMessage().trim().startsWith("[SQLITE_BUSY]"))
                        throw e;
                    Logger.warn("Database is currently locked and can't be accessed. Waiting for {} second(s) before attemting again. [{}/{}]", RETRY_N_SECONDS_IF_DB_LOCKED, counter, ABORT_DB_LOCK_WAITING_AFTER_N_RETRIES);
                    Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_N_SECONDS_IF_DB_LOCKED));
                }
            }
            Logger.info("Batch request finished successfully. Database is now up to date!");
            job.stage = PipelineStage.DB_UPDATED;
        } catch(Exception e) {
            throw Utility.rethrow(e);
        }
    }

    @Override
    public void updateXML(ImdbJob job) throws Exception {
        Logger.info("Updating XML fallback files for " + job.items.size() + " item(s).");
        int n = job.items.size()/LIST_PARTITIONS;
        var sublists = Lists.partition(job.items, n == 0 ? 1 : n);
        var factory = DocumentBuilderFactory.newInstance();
        AtomicInteger counter = new AtomicInteger();
        HashMap<Future<Void>, ImdbXmlWorker> map = new HashMap<>();
        var nofile = Collections.synchronizedCollection(new ArrayList<String>());
        for(var sub : sublists) {
            var worker = new ImdbXmlWorker(sub, factory.newDocumentBuilder(), counter, job.items.size(), nofile, configuration.metadataRoot);
            map.put(service.submit(worker), worker);
        }
        Throwable t = null;
        List<List<ImdbMetadataResult>> cleanup = new ArrayList<>();
        for(var entry : map.entrySet()) {
            try {
                entry.getKey().get();
            } catch(ExecutionException e) {
                t = e.getCause();
            }
            cleanup.add(entry.getValue().completed);
        }
        for(var c : cleanup)
            job.items.removeAll(c);
        if(nofile.size() > 0 && configuration.capabilities.contains(Capabilities.VERBOSE_XML_ERROR_LOG)) {
            String errorFile = "xml-error-" + job.uuid + "-" + job.library + ".log";
            Logger.warn(nofile.size() + " XML file(s) have failed to be updated due to them not being present on the file system.");
            Logger.warn("This is not an issue as they're not important for Plex as it reads the ratings from the database.");
            Logger.warn("The files have been dumped as " + errorFile + " in the PWD.");
            ErrorReports.fileReport(nofile, errorFile);
        }
        if(t != null)
            throw Utility.rethrow(t);
        Logger.info("Completed updating of XML fallback files.");
        job.stage = PipelineStage.COMPLETED;
    }

}
