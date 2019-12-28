package imdbupdater.imdb;

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
import common.OmdbApi.OMDBResponse;

public class ImdbOmdbCache {
    private long purgeMillis;
    private final ConcurrentHashMap<String, OMDBResponse> data;

    private ImdbOmdbCache(int purgeOlderThanNdays, ConcurrentHashMap<String, OMDBResponse> data) {
        if(data == null) {
            this.data = new ConcurrentHashMap<>();
        } else {
            this.data = data;
        }
        purgeMillis = TimeUnit.DAYS.toMillis(purgeOlderThanNdays);
    }

    private ImdbOmdbCache(int purgeOlderThanNdays) {
        this(purgeOlderThanNdays, null);
    }

    public static ImdbOmdbCache of(Path p, int purgeOlderThanNdays) {
        try {
            @SuppressWarnings("serial")
            HashMap<String, OMDBResponse> m = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8), new TypeToken<HashMap<String, OMDBResponse>>() {}.getType());
            return new ImdbOmdbCache(purgeOlderThanNdays, new ConcurrentHashMap<>(m)).purge();
        } catch (JsonSyntaxException | IOException e) {
            return new ImdbOmdbCache(purgeOlderThanNdays, null);
        }
    }

    public static void dump(Path p, ImdbOmdbCache data) throws IOException {
        Files.writeString(p, new Gson().toJson(data.data), StandardCharsets.UTF_8);
    }

    public ImdbOmdbCache purge() {
        data.entrySet().removeIf(e -> System.currentTimeMillis() - purgeMillis >= e.getValue().created());
        return this;
    }

    public void cache(String imdbId, OMDBResponse value) {
        data.putIfAbsent(imdbId, value);
    }

    public boolean isCached(String imdbId) {
        return data.get(imdbId) != null;
    }

    public OMDBResponse get(String imdbId) {
        return data.get(imdbId);
    }

}
