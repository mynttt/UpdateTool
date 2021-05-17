package updatetool.imdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.tinylog.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import updatetool.Main;
import updatetool.api.Implementation;
import updatetool.api.JobReport.StatusCode;
import updatetool.common.Capabilities;
import updatetool.common.DatabaseSupport;
import updatetool.common.DatabaseSupport.Library;
import updatetool.common.KeyValueStore;
import updatetool.common.SqliteDatabaseProvider;
import updatetool.common.Utility;
import updatetool.common.externalapis.AbstractApi.ApiVersion;
import updatetool.common.externalapis.TmdbApiV3;
import updatetool.common.externalapis.TvdbApiV3;
import updatetool.common.externalapis.TvdbApiV4;
import updatetool.exceptions.ApiCallFailedException;
import updatetool.exceptions.ImdbDatasetAcquireException;
import updatetool.imdb.ImdbPipeline.ImdbPipelineConfiguration;

public class ImdbDockerImplementation extends Implementation {
    private static final Set<Long> IGNORE_LIBRARIES = new HashSet<>(),
                                   UNLOCK_FOR_NEW_TV_AGENT = new HashSet<>();
    
    private int runEveryNhour = 12;
    private String apikeyTmdb, apiauthTvdb;
    private Path plexdata;
    private Runnable job;

    public ImdbDockerImplementation(String id, String desc, String usage, String help) {
        super(id, desc, usage, help);
    }
    
    @Override
    @SuppressFBWarnings("DM_EXIT")
    public void bootstrap(Map<String, String> args) throws Exception {
        apikeyTmdb = System.getenv("TMDB_API_KEY");
        String tvdbAuthLegacy = System.getenv("TVDB_AUTH_STRING");
        String tvdbApiKey = System.getenv("TVDB_API_KEY");
        String data = System.getenv("PLEX_DATA_DIR");
        String ignore = System.getenv("IGNORE_LIBS");
        String capabilitiesEnv = System.getenv("CAPABILITIES");
        String unlockForNewTvAgent = System.getenv("UNLOCK_FOR_NEW_TV_AGENT");
        
        EnumSet<Capabilities> capabilities = EnumSet.allOf(Capabilities.class);
        final List<Capabilities> parsed = new ArrayList<>();

        if(capabilitiesEnv != null && !capabilitiesEnv.isBlank()) {
            try {
                parsed.addAll(Arrays
                        .stream(capabilitiesEnv.split(";"))
                        .map(Capabilities::valueOf)
                        .collect(Collectors.toList()));
            } catch(IllegalArgumentException e) {
                String[] msg = e.getMessage().split("\\.");
                Logger.error("Invalid CAPABILITIES value: " + msg[msg.length-1]);
                System.exit(-1);
            }
        }
        
        if(!parsed.isEmpty()) {
            capabilities.removeAll(Capabilities
                    .getUserFlags()
                    .stream()
                    .filter(f -> !parsed.contains(f))
                    .collect(Collectors.toList())
                    );
        } else {
            capabilities.removeAll(Capabilities.getUserFlags());
        }

        Objects.requireNonNull(data, "Environment variable PLEX_DATA_DIR is not set");
        plexdata = Path.of(data);

        if(!Files.exists(plexdata) && !Files.isDirectory(plexdata)) {
            Logger.error("Directory: " + plexdata.toAbsolutePath().toString() + " does not exist.");
            System.exit(-1);
        }

        if(apikeyTmdb == null || apikeyTmdb.isBlank()) {
            Logger.info("No TMDB API key detected. Will not process TMDB backed Movie and TV Series libraries and TMDB orphans.");
            capabilities.remove(Capabilities.TMDB);
        } else {
            //TODO: switch between v3/v4
            new TmdbApiV3(apikeyTmdb, null, null, null, null);
            Logger.info("TMDB API key enabled TMDB <=> IMDB matching. Will process TMDB backed Movie and TV Series libraries and TMDB orphans.");
        }
        
        if(tvdbAuthLegacy != null && !tvdbAuthLegacy.isBlank()) {
            Logger.warn("Don't use legacy environment variable TVDB_AUTH_STRING. Use TVDB_API_KEY instead by only providing the TVDB API key.");
            String[] info = tvdbAuthLegacy.split(";");
            if(info.length == 3) {
                tvdbApiKey = info[2];
            } else {
                Logger.error("Invalid TVDB API authorization string given. Must contain 3 items seperated by a ';'. Will ignore TV Series with the TVDB agent.");
            }
        }
        
        if(tvdbApiKey == null || tvdbApiKey.isBlank()) {
            Logger.info("No TVDB API authorization string detected. Will not process TVDB backed Movie and TV Series libraries.");
            capabilities.remove(Capabilities.TVDB);
        } else {
            ApiVersion version;
            if(tvdbApiKey.length() == 16 || tvdbApiKey.length() >= 32) {
                tvdbApiKey = tvdbApiKey.trim();
                version = new TvdbApiV3(tvdbApiKey, null, null, null, null).version();
            } else {
                version = new TvdbApiV4(tvdbApiKey, null, null, null, null, null).version();
            }
            apiauthTvdb = tvdbApiKey;
            Logger.info("TVDB API ({}) authorization enabled IMDB rating update for Movies and TV Series with the TVDB agent.", version);
        }

        runEveryNhour = Utility.parseHourIntOrFallback(args.get("schedule"), runEveryNhour, id + " {schedule}");

        Logger.info("Starting IMDB Watchdog");
        Logger.info("Plex data dir: " + plexdata.toAbsolutePath().toString());

        var caches = Map.of("tmdb", KeyValueStore.of(Main.PWD.resolve("cache-tmdb2imdb.json")),
                            "tmdb-blacklist", KeyValueStore.of(Main.PWD.resolve("cache-tmdbBlacklist.json")),
                            "tmdb-series", KeyValueStore.of(Main.PWD.resolve("cache-tmdbseries2imdb.json")),
                            "tmdb-series-blacklist", KeyValueStore.of(Main.PWD.resolve("cache-tmdbseriesBlacklist.json")),
                            "tvdb", KeyValueStore.of(Main.PWD.resolve("cache-tvdb2imdb.json")),
                            "tvdb-blacklist", KeyValueStore.of(Main.PWD.resolve("cache-tvdbBlacklist.json")),
                            "tvdb-movie", KeyValueStore.of(Main.PWD.resolve("cache-tvdbMovie.json")),
                            "tvdb-movie-blacklist", KeyValueStore.of(Main.PWD.resolve("cache-tvdbMovieBlacklist.json")),
                            "new-agent-mapping", KeyValueStore.of(Main.PWD.resolve("new-agent-mapping.json")),
                            "tvdb-legacy-mapping", KeyValueStore.of(Main.PWD.resolve("tvdb-legacy-mapping.json")));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                caches.values().forEach(KeyValueStore::dump);
            } catch (Exception e) {
                Logger.error("Failed to save cache.");
                Logger.error(e);
            }
        }));
        
        if(ignore != null && !ignore.isBlank()) {
            String[] candidates = ignore.split(";");
            for(var s : candidates) {
                try {
                    long i = Long.parseLong(s);
                    IGNORE_LIBRARIES.add(i);
                    Logger.info("Ignoring library with ID: {}", i);
                } catch(NumberFormatException e) {}
            }
        }
        
        if(unlockForNewTvAgent != null && !unlockForNewTvAgent.isBlank()) {
            String[] candidates = unlockForNewTvAgent.split(";");
            for(var s : candidates) {
                try {
                    long i = Long.parseLong(s);
                    UNLOCK_FOR_NEW_TV_AGENT.add(i);
                    Logger.info("Unlocking library for IMDB TV Show rating update for the new TV Show agent: {}", i);
                } catch(NumberFormatException e) {}
            }
        }

        Logger.info("Capabilities: " + capabilities.toString());
        
        var dbLocation = plexdata.resolve("Plug-in Support/Databases/com.plexapp.plugins.library.db").toAbsolutePath().toString();
        var config = new ImdbPipelineConfiguration(apikeyTmdb, apiauthTvdb, plexdata.resolve("Metadata/Movies"), dbLocation, capabilities);
        job = new ImdbBatchJob(Main.EXECUTOR, config, plexdata, caches, capabilities);
    }

    private static class ImdbBatchJob implements Runnable {
        private final ImdbPipelineConfiguration config;
        private final ExecutorService service;
        private final Map<String, KeyValueStore> caches;
        private final EnumSet<Capabilities> capabilities;
        
        public ImdbBatchJob(ExecutorService service, ImdbPipelineConfiguration config, Path plexdata, Map<String, KeyValueStore> caches, EnumSet<Capabilities> capabilities) {
            this.service = service;
            this.config = config;
            this.caches = caches;
            this.capabilities = capabilities;
        }

        @Override
        @SuppressFBWarnings("DM_EXIT")
        public void run() {
            KeyValueStore.expiredCheck(30, caches.get("tvdb-blacklist"));
            KeyValueStore.expiredCheck(30, caches.get("tmdb-series-blacklist"));
            KeyValueStore.expiredCheck(30, caches.get("tmdb-blacklist"));
            KeyValueStore.expiredCheck(30, caches.get("tvdb-movie-blacklist"));
            KeyValueStore.expiredCheck(3, caches.get("tvdb-legacy-mapping"));
            
            List<Library> libraries = new ArrayList<>();
            ImdbLibraryMetadata metadata = null;
            
            try(var connection = new SqliteDatabaseProvider(config.dbLocation)) {
                var support = new DatabaseSupport(connection);
                if(!capabilities.contains(Capabilities.NO_MOVIE))
                    libraries.addAll(support.requestMovieLibraries(capabilities));
                if(!capabilities.contains(Capabilities.NO_TV))
                    libraries.addAll(support.requestSeriesLibraries(capabilities));
                libraries.removeIf(l -> IGNORE_LIBRARIES.contains(l.id));
                libraries.removeIf(l -> {
                    if(!l.agent.equals("tv.plex.agents.series"))
                        return false;
                    if(!UNLOCK_FOR_NEW_TV_AGENT.contains(l.id)) {
                        Logger.info("New TV Show Agent Library with ID: {} will not be processed by UpdateTool. Please register with environment variable UNLOCK_FOR_NEW_TV_AGENT to unlock proceessing capabilities.", l.id);
                        return true;
                    }
                    return false;
                });
                metadata = ImdbLibraryMetadata.fetchAll(libraries, new ImdbDatabaseSupport(connection, caches.get("new-agent-mapping")), config); 
            } catch(Exception e) {
                Logger.error(e.getClass().getSimpleName() + " exception encountered...");
                Logger.error("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
                Logger.error("========================================");
                Logger.error(e);
                Logger.error("========================================");
                Logger.error("The application will terminate now.");
                System.exit(-1);
            }
            
            var sorted = new ArrayList<>(IGNORE_LIBRARIES);
            Collections.sort(sorted);
            Logger.info("Library IDs on ignore list: {}", sorted);
            
            if(libraries.isEmpty()) {
                Logger.info("No libraries found. Sleeping until next invocation...");
                return;
            }
            
            try(var scraper = new ImdbScraper()) {
                var jobs = new ArrayDeque<ImdbJob>();
                var pipeline = new ImdbPipeline(metadata, service, caches, config, ImdbRatingDatasetFactory.requestSet(), scraper);
                var runner = new ImdbJobRunner();
                for(var lib : libraries) {
                    jobs.add(new ImdbJob(lib));
                    Logger.info("[{}] {} has {} item(s)", lib.type, lib.name, lib.items);
                }
                while(!jobs.isEmpty()) {
                    var job = jobs.pop();
                    Logger.info("Processing [{}] {} with UUID {} at stage: {}", job.libraryType, job.library, job.uuid, job.stage);
                    var result = runner.run(job, pipeline);
                    Logger.info("Job returned " + result.code + " : " + result.userDefinedMessage);
                    if(result.code == StatusCode.PASS) {
                        Logger.info("Job finished successfully for [{}] {} with UUID {}", job.libraryType, job.library, job.uuid);
                    }
                    if(result.code == StatusCode.API_ERROR) {
                        Logger.error(result.userDefinedMessage);
                        Logger.error("Original message: {}", result.exception.getMessage());
                        Logger.info("Aborting queue due to failing to fetch data from a called API. Will wait until next invocation.");
                        return;
                    }
                    if(result.code == StatusCode.ERROR) {
                        throw Utility.rethrow(result.exception);
                    }
                }
                caches.values().forEach(KeyValueStore::dump);
                Logger.info("Completed batch successfully.");
            } catch(ApiCallFailedException e) {
                Logger.error("{} encountered with message: {}", e.getClass().getSimpleName(), e.getMessage());
                Logger.info("Aborting queue due to API error. Will wait until next invocation.");
            } catch(ImdbDatasetAcquireException e) {
                Logger.error("Failed to acquire IMDB dataset due to {}.", e.getCause().getClass().getSimpleName());
                Logger.error("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
                Logger.error("========================================");
                Logger.error(e);
                Logger.error("========================================");
                try { Thread.sleep(10); } catch (InterruptedException e1) {} // Let the separate logger thread print the stack trace before printing the info
                Logger.info("Aborting queue due to failing to retrieve the IMDB data set. Will wait until next invocation.");
            } catch(Exception e) {
                Logger.error(e.getClass().getSimpleName() + " exception encountered...");
                Logger.error("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
                Logger.error("========================================");
                Logger.error(e);
                Logger.error("========================================");
                Logger.error("The application will terminate now.");
                System.exit(-1);
            }
        }

    }

    @Override
    public Runnable entryPoint() {
        return job;
    }

    @Override
    public int scheduleEveryHours() {
        return runEveryNhour;
    }
}
