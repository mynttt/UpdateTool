package updatetool.imdb;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sqlite.SQLiteException;
import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.api.ExportedRating;
import updatetool.api.Pipeline;
import updatetool.common.Capabilities;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.common.KeyValueStore;
import updatetool.common.SqliteDatabaseProvider;
import updatetool.common.Utility;
import updatetool.common.externalapis.TmdbApiV3;
import updatetool.common.externalapis.TvdbApiV3;
import updatetool.common.externalapis.TvdbApiV4;
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

    private static final int RETRY_N_SECONDS_IF_DB_LOCKED = 20;
    private static final int ABORT_DB_LOCK_WAITING_AFTER_N_RETRIES = 500;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    
    private final Collection<KeyValueStore> caches;
    private final ImdbScraper scraper;
    private final ImdbLibraryMetadata metadata;
    private final ExecutorService service;
    private final ImdbRatingDataset dataset;
    private final ImdbPipelineConfiguration configuration;
    private final HashMap<String, AgentResolvementStrategy<ImdbMetadataResult>> resolveMovies = new HashMap<>(), resolveSeries = new HashMap<>();
    private final EnumMap<LibraryType, HashMap<String, AgentResolvementStrategy<ImdbMetadataResult>>> resolvers = new EnumMap<>(LibraryType.class);
    public final AgentResolvementStrategy<ImdbMetadataResult> resolveDefault = new DefaultResolvement();

    public static class ImdbPipelineConfiguration {
        private final EnumSet<Capabilities> capabilities;
        public final String tmdbApiKey, tvdbApiKey, dbLocation, executeUpdatesOverPlexSqliteVersion;
        public final Path metadataRoot;
        public final boolean isTvdbV4;
        
        public ImdbPipelineConfiguration(String tmdbApiKey, String tvdbApiKey, Path metadataRoot, String dbLocation, String executeUpdatesOverPlexSqliteVersion, EnumSet<Capabilities> capabilities) {
            this.tmdbApiKey = tmdbApiKey;
            this.tvdbApiKey = tvdbApiKey;
            this.metadataRoot = metadataRoot;
            this.dbLocation = dbLocation;
            this.capabilities =  capabilities;
            this.executeUpdatesOverPlexSqliteVersion = executeUpdatesOverPlexSqliteVersion;
            this.isTvdbV4 = resolveTvdb() ? !(tvdbApiKey.length() == 16 || tvdbApiKey.length() >= 32) : false;
        }

        public boolean resolveTmdb() {
            return capabilities.contains(Capabilities.TMDB);
        }
        
        public boolean resolveTvdb() {
            return capabilities.contains(Capabilities.TVDB);
        }
        
        public boolean executeUpdatesOverPlexSqliteBinary() {
            return executeUpdatesOverPlexSqliteVersion != null && !executeUpdatesOverPlexSqliteVersion.isBlank();
        }
    }
    
    public ImdbPipeline(ImdbLibraryMetadata metadata, ExecutorService service, Map<String, KeyValueStore> caches, ImdbPipelineConfiguration configuration, ImdbRatingDataset dataset, ImdbScraper scraper) throws ApiCallFailedException {
        this.service = service;
        this.metadata = metadata;
        this.configuration = configuration;
        this.dataset = dataset;
        this.scraper = scraper;
        this.caches = caches.values();
        
        var tmdbResolver = configuration.resolveTmdb() ? new TmdbToImdbResolvement(new TmdbApiV3(configuration.tmdbApiKey, caches.get("tmdb-series"), caches.get("tmdb"), caches.get("tmdb-series-blacklist"), caches.get("tmdb-blacklist"))) : resolveDefault;
        var tvdbResolver = configuration.resolveTvdb() ? new TvdbToImdbResolvement(configuration.isTvdbV4 
                ? new TvdbApiV4(configuration.tvdbApiKey, caches.get("tvdb"), caches.get("tvdb-blacklist"), caches.get("tvdb-movie"), caches.get("tvdb-movie-blacklist"), caches.get("tvdb-legacy-mapping"))
                : new TvdbApiV3(configuration.tvdbApiKey, caches.get("tvdb"), caches.get("tvdb-blacklist"), caches.get("tvdb-movie"), caches.get("tvdb-movie-blacklist"))) 
                : resolveDefault;
        
        TmdbToImdbResolvement r1 = tmdbResolver == resolveDefault ? null : (TmdbToImdbResolvement) tmdbResolver;
        TvdbToImdbResolvement r2 = tvdbResolver == resolveDefault ? null : (TvdbToImdbResolvement) tvdbResolver;
        var newAgentResolver = new NewPlexAgentToImdbResolvement(caches.get("new-agent-mapping"), r1, r2, metadata);
        
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
        
        Logger.info("Starting watchdog to print progress to std::out with a delay and interval of 1 minute...");
        int max = items.size();
        AtomicInteger current = new AtomicInteger();
        
        var handle = SCHEDULER.scheduleWithFixedDelay(() -> {
            Logger.info("Current metadata resolvement status: [{}/{}] ({} %) - Next update in 1 minute.", current.get(), max, String.format("%.2f", ((current.get()*1.0/max)*100)));
        }, 1, 1, TimeUnit.MINUTES);
        
        
        for(var item : items) {
            var matcher = RESOLVEMENT.matcher(item.guid);
            if(matcher.find()) {
                for(var entry : resolve.entrySet()) {
                    if(matcher.group(entry.getKey()) == null)
                        continue;
                    resolverTasks.add(CompletableFuture.supplyAsync(() -> entry.getValue().resolve(item), service)
                            .thenAccept(b -> { current.incrementAndGet(); if(b) resolved.add(item); }));
                    break;
                }
            } else {
                resolveDefault.resolve(item);
            }
        }
        
        resolverTasks.stream().forEach(CompletableFuture::join);
        Logger.info("Progress printing watchdog has been stopped. Cancelation status: {}", handle.cancel(true));
        
        Logger.info("Save point: Persisting caches to keep queried look-up data in case of crashes or hang-ups.");
        caches.forEach(KeyValueStore::dump);
        
        int resolvedSize = resolved.size();
        int itemsSize = items.size();
        
        Logger.info("Filtered " + (itemsSize-resolvedSize) + " invalid item(s) (Library=" + job.library + ").");
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
        job.items.forEach(i -> map.put(i, dataset.getRatingFor(ImdbTransformer.clean(i.imdbId), i.title, scraper)));
        
        // Threading not possible because of IMDB rate limit
        map.values().forEach(ExportedRating::ensureAvailability);
        var noUpdate = map.entrySet().stream().filter(Predicate.not(ImdbTransformer::needsUpdate)).collect(Collectors.toSet());
        
        if(!noUpdate.isEmpty()) {
            Logger.info(noUpdate.size() + " item(s) need no update. (Library=" + job.library + ")");
            for(var item : noUpdate) {
                map.remove(item.getKey());
                job.items.remove(item.getKey());
            }
        }
        Logger.info("Transforming " + map.size() + " item(s) (Library=" + job.library +")");
        map.entrySet().stream().forEach(ImdbTransformer::updateMetadata);
        Logger.info("Transformed entries for " + map.size() + " items(s) (Library=" + job.library + ").");
        
        Logger.info("Save point: Persisting caches to keep queried look-up data in case of crashes or hang-ups.");
        caches.forEach(KeyValueStore::dump);
        
        job.stage = PipelineStage.TRANSFORMED_META;
    }

    @Override
    public void updateDatabase(ImdbJob job) throws Exception {
        if(job.items.isEmpty()) {
            Logger.info("Nothing to update. Skipping...");
            job.stage = PipelineStage.COMPLETED;
            return;
        }
        Logger.info("Updating " + job.items.size() + " via batch request...");
        int counter = 0;
        try(var connection = new SqliteDatabaseProvider(configuration.dbLocation)) {
            var db = new ImdbDatabaseSupport(connection, null,configuration);
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
            job.stage = PipelineStage.COMPLETED;
        } catch(Exception e) {
            throw Utility.rethrow(e);
        }
    }

}
