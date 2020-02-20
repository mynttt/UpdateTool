package updatetool.imdb.resolvement;

import java.net.http.HttpResponse;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.tinylog.Logger;
import com.google.gson.Gson;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.KeyValueStore;
import updatetool.common.TvdbApi;
import updatetool.common.Utility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class TvdbToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private static final Pattern EPISODE = Pattern.compile("[0-9]+\\/[0-9]+\\/[0-9]+");
    private static final Pattern SEASON = Pattern.compile("[0-9]+\\/[0-9]+");
    private static final Pattern SERIES = Pattern.compile("[0-9]+");
    private static final int MAX_TRIES = 3;
    
    private final Gson gson;
    private final KeyValueStore cache, blacklist;
    private final TvdbApi api;

    public TvdbToImdbResolvement(KeyValueStore cache, KeyValueStore blacklist, TvdbApi api) {
        this.cache = cache;
        this.blacklist = blacklist;
        this.api = api;
        this.gson = new Gson();
    }
    
    private class ApiResult {
        boolean success;
        String message;
        
        public ApiResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    
    private class Unmarshal {
        private class Data {
            public String imdbId;
        }
        private Data data;
    }
    
    private class UnmarshalEpisode {
        private class Data {
            public String imdbId;
        }
        private Data[] data;
    }
    
    private class UnmarshalError {
        String Error;
    }
    
    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        String tvdbId = ImdbUtility.extractId(ImdbUtility.TVDB, toResolve.guid);
        if(tvdbId == null) {
            Logger.error("Item: {} is detected as TVDB but has no id. (guid={})", toResolve.title, toResolve.guid);
            return false;
        }
        var lookup = cache.lookup(tvdbId);
        if(lookup != null) {
            toResolve.imdbId = lookup;
            return true;
        }
        if(blacklist.lookup(tvdbId) != null)
            return false;
        return resolveUncached(toResolve, tvdbId, categorize(tvdbId));
    }
    
    private int categorize(String tvdbId) {
        if(EPISODE.matcher(tvdbId).find())
            return 2;
        if(SEASON.matcher(tvdbId).find())
            return 1;
        if(SERIES.matcher(tvdbId).find())
            return 0;
        throw new IllegalArgumentException("This should never happen! Input was: " + tvdbId);
    }

    private boolean resolveUncached(ImdbMetadataResult toResolve, String tvdbId, int category) {
        switch(category) {
            case 0:
                return resolveSeries(tvdbId, toResolve);
            case 1:
                return resolveSeason(tvdbId, toResolve);
            case 2:
                return resolveEpisode(tvdbId, toResolve);
            default:
                throw new UnsupportedOperationException();
        }
    }
    
    private boolean resolveSeries(String tvdbId, ImdbMetadataResult toResolve) {
        String[] parts = tvdbId.split("/");
        var response = query(() -> api.seriesImdbId(parts[0]), tvdbId);
        if(!response.success) {
            Logger.warn("TVDB API failed for {}: {}", tvdbId, response.message);
            return false;
        }
        var data = gson.fromJson(response.message, Unmarshal.class).data;
        if(data == null || data.imdbId == null || data.imdbId.isBlank()) {
            Logger.warn("TVDB item {} with id {} does not have an IMDB id associated.", toResolve.title, tvdbId);
            blacklist.cache(tvdbId, "x");
            return false;
        }
        cache.cache(tvdbId, data.imdbId);
        toResolve.imdbId = data.imdbId;
        return true;
    }
    
    private boolean resolveSeason(String tvdbId, ImdbMetadataResult toResolve) {
        // If ever added to IMDB could be implemented here
        return false;
    }
    
    private boolean resolveEpisode(String tvdbId, ImdbMetadataResult toResolve) {
        String parts[] = tvdbId.split("/");
        var response = query(() -> api.episodeImdbId(parts), tvdbId);
        if(!response.success) {
            Logger.warn("TVDB API failed for {}: {}", tvdbId, response.message);
            return false;
        }
        var data = gson.fromJson(response.message, UnmarshalEpisode.class).data;
        if(data == null || data[0] == null || data[0].imdbId == null || data[0].imdbId.isBlank()) {
            Logger.warn("TVDB item {} with id {} does not have an IMDB id associated.", toResolve.title, tvdbId);
            blacklist.cache(tvdbId, "x");
            return false;
        }
        cache.cache(tvdbId, data[0].imdbId);
        toResolve.imdbId = data[0].imdbId;
        return true;
    }
    
    private ApiResult query(Supplier<HttpResponse<String>> supplier, String tvdbId) {
        HttpResponse<String> response = null;
        Exception ex = null;
        
        for(int i = 0; i < MAX_TRIES; i++) {
            try {
                response = supplier.get();
                if(response.statusCode() == 200)
                    return new ApiResult(true, response.body());
                if(response.statusCode() == 404) {
                    blacklist.cache(tvdbId, "x");
                    return new ApiResult(false, response.body());
                }
                Logger.warn("TVDB API returned a reply with status code != 200. Trying again... {}/{}", i+1, MAX_TRIES);
            } catch(Exception e) {
                ex = e;
                Logger.warn("TVDB API request failed: [" + (i+1) + "/" + MAX_TRIES +"] : " + e.getMessage());
                Logger.warn("Dumping response:" + response);
            }
        }
        
        if(ex != null)
            throw Utility.rethrow(ex);
        
        var error = gson.fromJson(response.body(), UnmarshalError.class);
        String msg = error.Error == null || error.Error.isBlank() ? "Code " + response.statusCode() : "Code " + response.statusCode() + " | " + error.Error;
        return new ApiResult(false, msg);
    }
}
