package updatetool.imdb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ImdbTmdbCache {
    private static final String TMDB2IMDB = "cache-tmdb2imdb.json";
    private final HashMap<String, String> tmdb2imdb = new HashMap<>();
    
    @SuppressWarnings("serial")
    public static ImdbTmdbCache of(Path p) {
        var cache = new ImdbTmdbCache();
        try {
            HashMap<String, String> m = new Gson().fromJson(Files.readString(p.resolve(TMDB2IMDB), StandardCharsets.UTF_8), new TypeToken<HashMap<String, String>>() {}.getType());
            cache.tmdb2imdb.putAll(m);
        } catch(JsonSyntaxException | IOException e) {}
        return cache;
    }

    public static void dump(Path p, ImdbTmdbCache data) throws Exception {
        Exception ex = null;
        try {
            Files.writeString(p.resolve(TMDB2IMDB), new Gson().toJson(data.tmdb2imdb), StandardCharsets.UTF_8);
        } catch(Exception e) { ex = e; }
        if(ex != null)
            throw ex;
    }
    
    public String lookupTmdb(String tmdbId) {
        return tmdb2imdb.get(tmdbId);
    }

    public void cacheTmdb(String tmdbId, String imdbId) {
        tmdb2imdb.put(tmdbId, imdbId);
    }
}
