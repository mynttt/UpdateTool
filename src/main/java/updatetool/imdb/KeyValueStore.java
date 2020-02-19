package updatetool.imdb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import updatetool.common.Utility;

public class KeyValueStore {
    private final HashMap<String, String> map = new HashMap<>();
    private final Path p;
    
    private KeyValueStore(Path p) {
        this.p = p;
    }
    
    @SuppressWarnings("serial")
    public static KeyValueStore of(Path p) {
        var cache = new KeyValueStore(p);
        try {
            HashMap<String, String> m = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8), new TypeToken<HashMap<String, String>>() {}.getType());
            cache.map.putAll(m);
        } catch(JsonSyntaxException | IOException e) {}
        return cache;
    }

    public void dump() {
        try {
            Files.writeString(p, new Gson().toJson(map), StandardCharsets.UTF_8);
        } catch(Exception e) { 
            throw Utility.rethrow(e);
        }
    }
    
    public String lookup(String key) {
        return map.get(key);
    }

    public void cache(String key, String value) {
        map.put(key, value);
    }
    
    public void reset() {
        map.clear();
    }
}
