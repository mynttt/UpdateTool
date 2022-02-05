package updatetool;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import org.tinylog.Logger;
import updatetool.common.KeyValueStore;

public final class Mitigations {
    private static final KeyValueStore MITIGATIONS = KeyValueStore.of(Main.PWD.resolve("mitigations.json"));
    private static final int VERSION = Integer.parseInt(Main.VERSION.replaceAll("(\\.|[A-Za-z]|\\-)", ""));
    
    private Mitigations() {};
    
    public static void executeMitigations() {
        executeTypoSwitchCacheResetMitigation();
        executeCacheParameterWrongOrderMitigation();
        executeCacheResetForImdbScraperUpdateMitigation();
        MITIGATIONS.dump();
    }
    
    private static void executeCacheResetForImdbScraperUpdateMitigation() {
        String KEY = "executeCacheResetForImdbScraperUpdateMitigation";
        
        if(MITIGATIONS.lookup(KEY) != null)
            return;
        
        Logger.info("One time mitigation executed: Reset IMDB webscraper caches for new Imdb web design update");
        Logger.info("This mitigation will only be executed once.");
        
        var idScrapeExpire = KeyValueStore.of(Main.PWD.resolve("imdbScrapeExpire.json"));
        var idCachedValue = KeyValueStore.of(Main.PWD.resolve("imdbScrapeValue.json"));
        
        idScrapeExpire.reset();
        idScrapeExpire.dump();
        idCachedValue.reset();
        idCachedValue.dump();
        
        Logger.info("Mitigation completed!");
        MITIGATIONS.cache(KEY, "");
    }
    
    private static void executeCacheParameterWrongOrderMitigation() {
        String KEY = "executeCacheParameterWrongOrderMitigation";
        
        if(MITIGATIONS.lookup(KEY) != null)
            return;
        
        if(VERSION > 154) {
            MITIGATIONS.cache(KEY, "");
            return;
        }
        
        Logger.info("One time mitigation executed: Switched caches in function parameter require conversion.");
        Logger.info("This mitigation will only be executed once.");
        
        var actualBlacklist = KeyValueStore.of(Main.PWD.resolve("cache-tvdb2imdb.json"));
        var actualCache = KeyValueStore.of(Main.PWD.resolve("cache-tvdbBlacklist.json"));
        actualCache.remove("__EXPIRE");
        
        actualCache.withChangedPath(Main.PWD.resolve("cache-tvdb2imdb.json")).dump();
        actualBlacklist.withChangedPath(Main.PWD.resolve("cache-tvdbBlacklist.json")).dump();
        
        Logger.info("Mitigation completed!");
        MITIGATIONS.cache(KEY, "");
    }
    
    private static void executeTypoSwitchCacheResetMitigation() {
        String KEY = "executeTypoSwitchCacheResetMitigation";
        
        if(MITIGATIONS.lookup(KEY) != null)
            return;
        
        if(VERSION > 151) {
            MITIGATIONS.cache(KEY, "");
            return;
        }
        
        Logger.info("One time mitigation executed: Typo in cache names requires a full cache reset.");
        Logger.info("This mitigation will only be executed once.");
        
        String[] reset = {
                "cache-tmdb2imdb.json           ",
                "cache-tmdbseriesBlacklist.json ", 
                "cache-tvdbMovie.json           ",
                "state-imdb.json                ",
                "cache-tmdbBlacklist.json       ",
                "cache-tvdb2imdb.json           ", 
                "cache-tvdbMovieBlacklist.json  ",
                "tvdb-legacy-mapping.json       ",
                "cache-tmdbseries2imdb.json     ",
                "cache-tvdbBlacklist.json       ", 
                "new-agent-mapping.json         ",
        };
        
        Arrays.stream(reset).map(String::strip).forEach(p -> {
            try {
                Files.deleteIfExists(Main.PWD.resolve(p));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        
        Logger.info("Mitigation completed!");
        MITIGATIONS.cache(KEY, "");
    }
}
