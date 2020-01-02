package updatetool.imdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.tinylog.Logger;
import updatetool.Main;
import updatetool.api.Implementation;
import updatetool.api.JobReport.StatusCode;
import updatetool.common.DatabaseSupport;
import updatetool.common.SqliteDatabaseProvider;
import updatetool.common.State;
import updatetool.common.Utility;
import updatetool.imdb.ImdbPipeline.ImdbPipelineConfiguration;

public class ImdbDockerImplementation implements Implementation {
    public int CACHE_PURGE_TIME_DAYS = 14;
    public int RUN_EVERY_N_HOUR = 12;

    private String apikeyOmdb, apikeyTmdb;
    private Path plexdata;

    @Override
    public void invoke(String[] args) throws Exception {
        apikeyOmdb = System.getenv("OMDB_API_KEY");
        apikeyTmdb = System.getenv("TMDB_API_KEY");
        String data = System.getenv("PLEX_DATA_DIR");

        Objects.requireNonNull(apikeyOmdb, "Environment variable OMDB_API_KEY is not set");
        Objects.requireNonNull(data, "Environment variable PLEX_DATA_DIR is not set");

        plexdata = Path.of(data);

        if(!Files.exists(plexdata) && !Files.isDirectory(plexdata)) {
            System.err.println("Directory: " + plexdata.toAbsolutePath().toString() + " does not exist.");
            System.exit(-1);
        }

        Main.testApiImdb(apikeyOmdb);

        if(apikeyTmdb == null || apikeyTmdb.trim().isEmpty()) {
            Logger.info("No TMDB API key detected. Will not attempt to do an TMDB <=> IMDB ID conversion to update TMDB matched items (unless already matched previously).");
        } else {
            Main.testApiTmdb(apikeyTmdb);
            Logger.info("TMDB API key enabled TMDB <=> IMDB matching. Will fetch IMDB ratings for non matched IMDB items.");
        }

        if(args.length == 2) {
            RUN_EVERY_N_HOUR = parseCommandInt(args[1], i -> i > 0, "Invalid parameter for: RUN_EVERY_N_HOUR (must be number and > 0)");
        }

        if(args.length >= 3) {
            RUN_EVERY_N_HOUR = parseCommandInt(args[1], i -> i > 0, "Invalid parameter for: RUN_EVERY_N_HOUR (must be number and > 0)");
            CACHE_PURGE_TIME_DAYS = parseCommandInt(args[2], i -> i >= 0, "Invalid parameter for: CACHE_PURGE_TIME_DAYS (must be number and >= 0)");
        }

        Logger.info("Starting IMDB Watchdog");
        Logger.info("Plex data dir: " + plexdata.toAbsolutePath().toString());
        Logger.info("Invoke every " + RUN_EVERY_N_HOUR + " hour(s)");
        Logger.info("Purge cache every " + CACHE_PURGE_TIME_DAYS + " day(s)");

        var state = State.recoverImdb(Main.STATE_IMDB);
        var cache = ImdbOmdbCache.of(Main.PWD, CACHE_PURGE_TIME_DAYS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ImdbOmdbCache.dump(Main.PWD, cache);
            } catch (Exception e) {
                Logger.error("Failed to save cache.");
                Logger.error(e);
            }
        }));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                State.dump(Main.STATE_IMDB, state);
            } catch (IOException e) {
                Logger.error("Failed to save state.");
                Logger.error(e);
            }
        }));

        if(!state.isEmpty())
            Logger.info("Loaded " + state.size() + " unfinished job(s).\n");

        var config = new ImdbPipelineConfiguration(apikeyOmdb, apikeyTmdb, plexdata.resolve("Metadata/Movies"));

        var scheduler = Executors.newSingleThreadScheduledExecutor();
        Logger.info("Running first task...");
        scheduler.schedule(new ImdbBatchJob(config, plexdata, cache, state), 1, TimeUnit.SECONDS);
        Logger.info("Scheduling next tasks to run @ every " + RUN_EVERY_N_HOUR + " hour(s)");
        scheduler.scheduleAtFixedRate(new ImdbBatchJob(config, plexdata, cache, state), 1, RUN_EVERY_N_HOUR, TimeUnit.HOURS);
    }

    private static class ImdbBatchJob implements Runnable {
        private final ImdbPipelineConfiguration config;
        private final ExecutorService service;
        private final ImdbOmdbCache cache;
        private final Set<ImdbJob> state;
        private final String dbLocation;

        public ImdbBatchJob(ImdbPipelineConfiguration config, Path plexdata, ImdbOmdbCache cache, Set<ImdbJob> state) {
            service = Executors.newFixedThreadPool(6);
            this.config = config;
            this.cache = cache;
            this.state = state;
            this.dbLocation = plexdata.resolve("Plug-in Support/Databases/com.plexapp.plugins.library.db").toAbsolutePath().toString();
            cache.purge();
        }

        @Override
        public void run() {
            SqliteDatabaseProvider connection = null;
            try {
                cache.purge();
                connection = new SqliteDatabaseProvider(dbLocation);
                var libraries = new DatabaseSupport(connection).requestLibraries();
                var jobs = new ArrayDeque<ImdbJob>();
                var db = new ImdbDatabaseSupport(connection);
                var pipeline = new ImdbPipeline(db, service, cache, config);
                var runner = new ImdbJobRunner();
                for(var lib : libraries) {
                    jobs.add(new ImdbJob(lib.name, lib.uuid));
                    Logger.info("Library: " + lib.name + " has " + lib.items + " item(s)");
                }
                while(!jobs.isEmpty()) {
                    var job = jobs.pop();
                    Logger.info("Processing library: " + job.library + " with UUID " + job.uuid + " at stage: " + job.stage);
                    var result = runner.run(job, pipeline);
                    Logger.info("Job returned " + result.code + " : " + result.userDefinedMessage);
                    if(result.code == StatusCode.PASS) {
                        Logger.info("Job finished successfully for library " + job.library + " with UUID " + job.uuid);
                        state.remove(job);
                    }
                    if(result.code == StatusCode.RATE_LIMIT) {
                        Logger.info("Aborting queue duo to being rate limited by the OMDB API. Will wait until next invocation.");
                        Logger.info("It is now safe to suspend execution if this tool should not run 24/7.");
                        try { connection.close(); } catch (Exception e) {}
                        return;
                    }
                    if(result.code == StatusCode.API_ERROR) {
                        Logger.error(result.userDefinedMessage);
                        Logger.error("Original message: {}", result.exception.getMessage());
                        Logger.info("Aborting queue duo to failing to fetch from the OMDB API. Will wait until next invocation.");
                        Logger.info("It is now safe to suspend execution if this tool should not run 24/7.");
                        try { connection.close(); } catch (Exception e) {}
                        return;
                    }
                    if(result.code == StatusCode.ERROR) {
                        try { connection.close(); } catch (Exception e) {}
                        throw Utility.rethrow(result.exception);
                    }
                }
                ImdbOmdbCache.dump(Main.PWD, cache);
                Logger.info("Completed batch successfully. Waiting till next invocation...");
                Logger.info("It is now safe to suspend execution if this tool should not run 24/7.");
                try { connection.close(); } catch (Exception e) {}
            } catch(Exception e) {
                if(connection!=null)
                    try { connection.close(); } catch (Exception e1) {}
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

    private static int parseCommandInt(String input, Function<Integer, Boolean> validation, String error) {
        int i = 0;
        try {
            i = Integer.parseInt(input);
        } catch( NumberFormatException e) {
            System.err.println(error);
            System.exit(-1);
        }
        if(!validation.apply(i)) {
            System.err.println(error);
            System.exit(-1);
        }
        return i;
    }

}
