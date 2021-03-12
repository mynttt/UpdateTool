package updatetool;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import org.tinylog.Logger;
import updatetool.common.KeyValueStore;

public final class Mitigations {
    private static final KeyValueStore MITIGATIONS = KeyValueStore.of(Main.PWD.resolve("mitigations.json"));
    
    private Mitigations() {};
    
    public static void executeMitigations() {
        executeTypoSwitchCacheResetMitigation();
        MITIGATIONS.dump();
    }
    
    private static void executeTypoSwitchCacheResetMitigation() {
        String KEY = "executeTypoSwitchCacheResetMitigation";
        
        if(MITIGATIONS.lookup(KEY) != null)
            return;
        
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
