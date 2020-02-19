package updatetool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.tinylog.Logger;
import updatetool.api.Implementation;
import updatetool.common.AbstractApi;
import updatetool.common.TmdbApi;
import updatetool.common.TvdbApi;
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

    public static String version() {
        return VERSION;
    }

    public enum Implementations {
        IMDB_DOCKER("imdb-docker",
                "Watchdog mode implementation, will update ratings every n hours",
                ImdbDockerImplementation.class,
                "Usage: this.jar imdb-docker [] | [{every_n_hour}] | [{every_n_hour} {cache_purge_in_days}]",
                new String[] { "The following environment variables must be set and exported before launching this tool successfully!",
                             "PLEX_DATA_DIR: Used for the data directory of plex",
                             "(Optional) TMDB_API_KEY: Used to convert TMDB matched items to IMDB items. The fallback will only be available if this is set.",
                             "(Optional) TVDB_AUTH_STRING: Used to auth with the TVDB API. Must be entered as a ';' seperated string of username, userid, apikey",
                             "Example: username;DAWIDK9CJKWFJAWKF;e33914feabd52e8192011b0ce6c8",
                             "",
                             "No parameters starts with the default of {every_n_hour} = 12, {cache_pruge_in_days} = 14 and {new_movie_cache_purge_threshold} = 12",
                             "{every_n_hour} : Invoke this every n hour on all IMDB supported libraries"});

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
        preLogPurge();
        
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

        var constructor = impls.get(0).entry.getConstructor(new Class[0]);
        constructor.newInstance().invoke(args);
    }

    public static void rollingLogPurge() throws IOException {
        var files = Files.list(Main.PWD)
                .filter(p -> p.getFileName().toString().startsWith("updatetool.")).collect(Collectors.toList());
        var keep = files.stream().max((p1, p2) -> {
            try {
                return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
            } catch (IOException e) {}
            return 0;
        });
        if(files.size() > 1) {
            keep.ifPresent(f -> files.remove(f));
            for(var f : files)
                Files.delete(f);
        }
    }

    private static void preLogPurge() throws IOException {
        var files = Files.list(Main.PWD)
            .filter(p -> p.getFileName().toString().startsWith("updatetool."))
            .collect(Collectors.toList());
        for(var p : files)
            Files.delete(p);
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
    
    public static void testApiTvdb(String[] credentials) {
        Logger.info("Testing TVDB API authorization: username={} | userkey={} | apikey={}", credentials[0], credentials[1], credentials[2]);
        try {
            new TvdbApi(credentials);
        } catch(IllegalArgumentException e) {
            Logger.error("API Test failed: " + e.getMessage());
            Logger.error("Keys available under: https://thetvdb.com/");
            System.exit(-1);
        }
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
