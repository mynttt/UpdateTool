package imdbupdater;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.tinylog.Logger;
import common.OmdbApi;
import imdbupdater.api.Implementation;
import imdbupdater.imdb.ImdbDockerImplementation;

public class Main {
    public static final Path PWD = Paths.get(".");
    public static final List<Implementations> IMPLEMENTATIONS = Arrays.asList(Implementations.values());

    public static final Path STATE_IMDB = Main.PWD.resolve("state-imdb.json");
    public static final Path CACHE_IMDB = Main.PWD.resolve("cache-imdb.json");

    public static boolean PRINT_STATUS = false;

    public enum Implementations {
        IMDB_DOCKER("imdb-docker",
                "Watchdog mode implementation, will update ratings every n hours",
                ImdbDockerImplementation.class,
                "Usage: this.jar imdb-docker [] | [{every_n_hour}] | [{every_n_hour} {cache_purge_in_days}]",
                new String[] { "Environment variables PLEX_DATA_DIR and OMDB_API_KEY are used for the data dir and the api key.",
                             "No parameters starts with the default of {every_n_hour} = 12, {cache_pruge_in_days} = 14 and {new_movie_cache_purge_threshold} = 12",
                             "{every_n_hour} : Invoke this every n hour on all IMDB supported libraries",
                             "{cache_purge_in_days} : Purge the responses for movies over the new movie threshold every n hours (will send more requests to OMDB the lower the number)"}),
        IMDB_CLI("imdb-cli",
                "Legacy CLI wizard implementation",
                LegacyImplementation.class,
                "Usage: this.jar imdb-cli {plexdata} {apikey}",
                new String[] {"{plexdata} : Plex data root, the folder that contains folders like Cache, Codecs, Media, Plug-ins, ...",
                "{apikey}   : OMDB API Key"});


        public final String id, description, help;
        public final String[] parameters;
        public final Class<? extends Implementation> entry;

        Implementations(String id, String description, Class<? extends Implementation> entry, String help, String[] parameters) {
            this.id = id;
            this.help = help;
            this.entry = entry;
            this.description = description;
            this.parameters = parameters;
        }

    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Logger.error("Uncaught " + e.getClass().getSimpleName() + " exception encountered...");
            Logger.error("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
            Logger.error("========================================");
            Logger.error(e);
            Logger.error("========================================");
            Logger.error("The application will terminate now.");
            System.exit(-1);
        });

        Class.forName("org.sqlite.JDBC");
        IMPLEMENTATIONS.sort((i1, i2) -> i1.id.compareTo(i2.id));

        if(args.length == 0) {
            printHelp();
            System.exit(-1);
        }

        var impls = IMPLEMENTATIONS.stream().filter(i -> i.id.equals(args[0])).collect(Collectors.toList());

        if(impls.isEmpty()) {
            System.err.println("Invalid id: " + args[0]);
            System.exit(-1);
        }

        if(impls.size() > 1) {
            System.err.println("duplicate id: " + args[0]);
            System.exit(-1);
        }

        var constructor = impls.get(0).entry.getConstructor(new Class[0]);
        constructor.newInstance().invoke(args);
    }

    public static void testApiImdb(String key) throws IOException, InterruptedException {
        Logger.info("Testing API key: " + key);
        var api = new OmdbApi(key);
        var response = api.testApi();
        if(response.statusCode() != 200) {
            Logger.error("API Test failed: Code " + response.statusCode());
            Logger.error("Payload:" + response.body());
            Logger.error("Key available under https://www.omdbapi.com/");
            System.exit(-1);
        }
        Logger.info("Test passed. API Key is valid.");
    }

    public static void printHelp(Implementations i, boolean datahint) {
        if(datahint)
            System.out.println("Data folder: https://support.plex.tv/articles/202915258-where-is-the-plex-media-server-data-directory-located");
        System.out.println("id: " + i.id);
        System.out.println("  description: " + i.description);
        System.out.println("  usage: " + i.help);
        System.out.println("  Parameters:");
        for(var p : i.parameters) {
            System.out.println("    " + p);
        }
    }

    public static void printHelp() {
        System.out.println("Data folder: https://support.plex.tv/articles/202915258-where-is-the-plex-media-server-data-directory-located");
        System.out.println();
        System.out.println("Usage: this.jar [id] {args ...}");
        System.out.println();
        for(var i : IMPLEMENTATIONS) {
            printHelp(i, false);
            System.out.println();
        }
    }

}
