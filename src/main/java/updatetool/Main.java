package updatetool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.tinylog.Logger;
import updatetool.api.Implementation;
import updatetool.common.AbstractApi;
import updatetool.common.OmdbApi;
import updatetool.common.TmdbApi;
import updatetool.imdb.ImdbDockerImplementation;

public class Main {
    public static final Path PWD = Paths.get(".");
    public static final List<Implementations> IMPLEMENTATIONS = Arrays.asList(Implementations.values());

    public static final Path STATE_IMDB = Main.PWD.resolve("state-imdb.json");

    private static String VERSION;

    static {
        try {
            VERSION = new String(Main.class.getResourceAsStream("/VERSION").readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            VERSION = "FAILED_TO_READ_VERSION";
        }
    }

    public enum Implementations {
        IMDB_DOCKER("imdb-docker",
                "Watchdog mode implementation, will update ratings every n hours",
                ImdbDockerImplementation.class,
                "Usage: this.jar imdb-docker [] | [{every_n_hour}] | [{every_n_hour} {cache_purge_in_days}]",
                new String[] { "The following environment variables must be set and exported before launching this tool successfully!",
                             "PLEX_DATA_DIR: Used for the data directory of plex",
                             "OMDB_API_KEY: Used to access the OMDB database and fetch IMDB ratings",
                             "(Optional) TMDB_API_KEY: Used to convert TMDB matched items to IMDB items. The fallback will only be available if this is set.",
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

        public static Implementations of(String string) {
            for(var v : values())
                if(v.id.equals(string))
                    return v;
            return null;
        }

    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Logger.error("Uncaught " + e.getClass().getSimpleName() + " exception encountered...");
            Logger.error("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
            Logger.error("========================================");
            Logger.error(e);
            Logger.error("========================================");
            Logger.error("The application will terminate now. (Version: " + VERSION + ")");
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

        Logger.info("Running version: " + VERSION);
        var constructor = impls.get(0).entry.getConstructor(new Class[0]);
        constructor.newInstance().invoke(args);
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

    public static void testApiTmdb(String apikeyTmdb) throws Exception {
        Logger.info("Testing TMDB API key: " + apikeyTmdb);
        var api = new TmdbApi(apikeyTmdb);
        genericApiTest(api);
    }

    public static void testApiImdb(String key) throws Exception {
        Logger.info("Testing OMDB API key: " + key);
        var api = new OmdbApi(key);
        genericApiTest(api);
    }

    private static void genericApiTest(AbstractApi api) throws Exception {
        var response = api.testApi();
        if(response.statusCode() != 200) {
            Logger.error("API Test failed: Code " + response.statusCode());
            Logger.error("Payload:" + response.body());
            Logger.error("Key available under:" + api.keysWhere());
            System.exit(-1);
        }
        Logger.info("Test passed. API Key is valid.");
    }

}
