package updatetool.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class KeyValueStore {
    @Override
    public String toString() {
        return "KeyValueStore [map=" + map + "]";
    }

    private final HashMap<String, String> map = new HashMap<>();
    private final Path p;
    
    private KeyValueStore(Path p) {
        this.p = p;
    }
    
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    @SuppressWarnings("serial")
    public static KeyValueStore of(Path p) {
        var cache = new KeyValueStore(p);
        try {
            HashMap<String, String> m = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8), new TypeToken<HashMap<String, String>>() {}.getType());
            cache.map.putAll(m);
        } catch(Exception e) {}
        return cache;
    }

    public void dump() {
        try {
            Files.writeString(p, new Gson().toJson(map), StandardCharsets.UTF_8);
        } catch(Exception e) { 
            throw Utility.rethrow(e);
        }
    }
    
    public synchronized String lookup(String key) {
        return map.get(key);
    }

    public synchronized void cache(String key, String value) {
        map.put(key, value);
    }
    
    public synchronized void reset() {
        map.clear();
    }
    
    public static void expiredCheck(int days, KeyValueStore store) {
        String expire = store.lookup("__EXPIRE");
        if(expire == null) {
            store.cache("__EXPIRE", Long.toString(System.currentTimeMillis()+TimeUnit.DAYS.toMillis(days)));
        } else {
            long l = Long.parseLong(expire);
            if(l <= System.currentTimeMillis()) {
                store.reset();
                store.cache("__EXPIRE", Long.toString(System.currentTimeMillis()+TimeUnit.DAYS.toMillis(days)));
            }
        }
    }
}
