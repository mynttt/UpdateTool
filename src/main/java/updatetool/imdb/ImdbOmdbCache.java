package updatetool.imdb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import updatetool.common.OmdbApi.OMDBResponse;

public class ImdbOmdbCache {
    private static final String IMDB = "cache-imdb.json";
    private static final String TMDB2IMDB = "cache-tmdb2imdb.json";

    private long purgeMillis;
    private final ConcurrentHashMap<String, OMDBResponse> data = new ConcurrentHashMap<>();
    private final HashMap<String, String> tmdb2imdb = new HashMap<>();

    private ImdbOmdbCache(int purgeOlderThanNdays) {
        purgeMillis = TimeUnit.DAYS.toMillis(purgeOlderThanNdays);
    }

    @SuppressWarnings("serial")
    public static ImdbOmdbCache of(Path p, int purgeOlderThanNdays) {
        var cache = new ImdbOmdbCache(purgeOlderThanNdays);
        try {
            HashMap<String, OMDBResponse> m = new Gson().fromJson(Files.readString(p.resolve(IMDB), StandardCharsets.UTF_8), new TypeToken<HashMap<String, OMDBResponse>>() {}.getType());
            cache.data.putAll(m);
        } catch (JsonSyntaxException | IOException e) {}
        try {
            HashMap<String, String> m = new Gson().fromJson(Files.readString(p.resolve(TMDB2IMDB), StandardCharsets.UTF_8), new TypeToken<HashMap<String, String>>() {}.getType());
            cache.tmdb2imdb.putAll(m);
        } catch(JsonSyntaxException | IOException e) {}
        return cache.purge();
    }

    public static void dump(Path p, ImdbOmdbCache data) throws Exception {
        Exception ex = null;
        try {
            Files.writeString(p.resolve(IMDB), new Gson().toJson(data.data), StandardCharsets.UTF_8);
        } catch (Exception e) { ex = e; }
        try {
            Files.writeString(p.resolve(TMDB2IMDB), new Gson().toJson(data.tmdb2imdb), StandardCharsets.UTF_8);
        } catch(Exception e) { ex = e; }
        if(ex != null)
            throw ex;
    }

    public ImdbOmdbCache purge() {
        data.entrySet().removeIf(e -> System.currentTimeMillis() - purgeMillis >= e.getValue().created());
        return this;
    }

    public void cacheOmdbResponse(String imdbId, OMDBResponse value) {
        data.putIfAbsent(imdbId, value);
    }

    public boolean isOmdbResponseCached(String imdbId) {
        return data.get(imdbId) != null;
    }

    public OMDBResponse getOmdbResponse(String imdbId) {
        return data.get(imdbId);
    }

    public String lookupTmdb(String tmdbId) {
        return tmdb2imdb.get(tmdbId);
    }

    public void cacheTmdb(String tmdbId, String imdbId) {
        tmdb2imdb.put(tmdbId, imdbId);
    }

}
