package updatetool.imdb;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.tinylog.Logger;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import updatetool.api.AgentResolvementStrategy;
import updatetool.api.Pipeline;
import updatetool.common.ErrorReports;
import updatetool.common.OmdbApi;
import updatetool.common.OmdbApi.OMDBResponse;
import updatetool.common.TmdbApi;
import updatetool.common.Utility;
import updatetool.exceptions.RatelimitException;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.resolvement.DefaultResolvement;
import updatetool.imdb.resolvement.ImdbResolvement;
import updatetool.imdb.resolvement.TmdbToImdbResolvement;

public class ImdbPipeline extends Pipeline<ImdbJob> {
    private static final Pattern RESOLVEMENT = Pattern.compile(
                "(?<IMDB>agents.imdb:\\/\\/tt)"
                + "|(?<TMDB>agents.themoviedb:\\/\\/)"
            );

    private static final int LIST_PARTITIONS = 16;
    private final ImdbDatabaseSupport db;
    private final ExecutorService service;
    private final ImdbOmdbCache cache;
    private final ImdbPipelineConfiguration configuration;
    private final HashMap<String, AgentResolvementStrategy<ImdbMetadataResult>> resolve = new HashMap<>();
    public final AgentResolvementStrategy<ImdbMetadataResult> resolveDefault = new DefaultResolvement();

    public static class ImdbPipelineConfiguration {
        public final String omdbApiKey, tmdbApiKey;
        public final Path metadataRoot;

        public ImdbPipelineConfiguration(String omdbApiKey, String tmdbApiKey, Path metadataRoot) {
            this.omdbApiKey = omdbApiKey;
            this.tmdbApiKey = tmdbApiKey;
            this.metadataRoot = metadataRoot;
        }

        public boolean resolveTmdbConflicts() {
            return tmdbApiKey != null;
        }
    }

    public ImdbPipeline(ImdbDatabaseSupport db, ExecutorService service, ImdbOmdbCache cache, ImdbPipelineConfiguration configuration) {
        this.db = db;
        this.service = service;
        this.cache = cache;
        this.configuration = configuration;
        resolve.put("IMDB", new ImdbResolvement());
        resolve.put("TMDB", new TmdbToImdbResolvement(cache, new TmdbApi(configuration.tmdbApiKey)));
    }

    @Override
    public void analyseDatabase(ImdbJob job) throws Exception {
        var items = db.requestEntries(db.requestLibraryIdOfUuid(job.uuid));
        Logger.info("Resolving IMDB identifiers for items. Only warnings and errors will show up...");
        Logger.info("Items that show up here will not be processed by further stages of the pipeline.");
        int skipped = 0;
        for(var it = items.iterator(); it.hasNext(); ) {
            var item = it.next();
            var matcher = RESOLVEMENT.matcher(item.guid);
            if(matcher.find()) {
                for(var entry : resolve.entrySet()) {
                    if(matcher.group(entry.getKey()) == null)
                        continue;
                    var resolved = entry.getValue().resolve(item);
                    if(!resolved)
                        it.remove();
                    break;
                }
            } else {
                resolveDefault.resolve(item);
                skipped++;
                it.remove();
            }
        }
        Logger.info("Filtered " + skipped + " invalid item(s).");
        job.stage = PipelineStage.ANALYSED_DB;
        job.items = items;
    }

    @Override
    public void accumulateMetadata(ImdbJob job) throws Exception {
        Logger.info("Items: " + job.items.size());
        var unprocessed = job.items.stream().filter(j -> !cache.isOmdbResponseCached(j.imdbId)).collect(Collectors.toList());
        Logger.info("Metadata missing for: " + unprocessed.size());
        Logger.info("Retrieving metadata...");
        int n = unprocessed.size()/LIST_PARTITIONS;
        var sublists = Lists.partition(unprocessed, n == 0 ? 1 : n);
        Gson gson = new Gson();
        HashMap<Future<Void>, ImdbOmdbWorker> map = new HashMap<>();
        boolean rateLimited = false;
        Throwable ex = null;
        AtomicInteger counter = new AtomicInteger();

        for(var sub : sublists) {
            ImdbOmdbWorker w = new ImdbOmdbWorker(sub, gson, new OmdbApi(configuration.omdbApiKey), counter, unprocessed.size(), cache);
            map.put(service.submit(w), w);
        }

        for(var entry : map.entrySet()) {
            try {
                entry.getKey().get();
            } catch(ExecutionException e) {
                Throwable t = e;
                while(t.getCause() != null)
                    t = t.getCause();
                if(t instanceof RatelimitException)
                    rateLimited = true;
                if(!rateLimited)
                    ex = e.getCause();
            }
        }

        if(rateLimited)
            throw new RatelimitException();

        if(ex != null)
            throw Utility.rethrow(ex);

        job.stage = PipelineStage.ACCUMULATED_META;
        Logger.info("Fetched all metadata from OMDb.");
    }

    @Override
    public void transformMetadata(ImdbJob job) throws Exception {
        var map = new HashMap<ImdbMetadataResult, OMDBResponse>();
        job.items.forEach(i -> map.put(i, cache.getOmdbResponse(i.imdbId)));
        var noUpdate = map.entrySet().stream().filter(ImdbTransformer::needsNoUpdate).collect(Collectors.toSet());
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
        Logger.info("Updating " + job.items.size() + " via batch request...");
        db.requestBatchUpdateOf(job.items);
        Logger.info("Batch request finished successfully. Database is now up to date!");
        job.stage = PipelineStage.DB_UPDATED;
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
        if(nofile.size()>0) {
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
