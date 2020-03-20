package updatetool.imdb.resolvement;

import java.net.http.HttpResponse;
import java.util.function.Supplier;
import org.tinylog.Logger;
import com.google.gson.Gson;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.KeyValueStore;
import updatetool.common.TmdbApi;
import updatetool.common.Utility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class TmdbSeriesToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private static final int MAX_TRIES = 3;

    private final Gson gson = new Gson();
    private final KeyValueStore cache, blacklist;
    private final TmdbApi api;
    
    private class ApiResult {
        private boolean success;
        private String message;
        
        private ApiResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    
    private class UnmarshalError {
        private int status_code;
        private String status_message;
    }
    
    private class Unmarshal {
        private String imdb_id;
    }
    
    public TmdbSeriesToImdbResolvement(KeyValueStore cache, KeyValueStore blacklist, TmdbApi api) {
        this.cache = cache;
        this.api = api;
        this.blacklist = blacklist;
    }

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        String tmdbId = ImdbUtility.extractId(ImdbUtility.TVDB_TMDB_SERIES_MATCHING, toResolve.guid);
        if(tmdbId == null) {
            Logger.error("Item: {} is detected as TMDB TV Show but has no id. (guid={})", toResolve.title, toResolve.guid);
            return false;
        }
        var lookup = cache.lookup(tmdbId);
        if(lookup != null) {
            toResolve.imdbId = lookup;
            return true;
        }
        if(blacklist.lookup(tmdbId) != null)
            return false;
        return resolveUncached(toResolve, tmdbId, categorize(tmdbId));
    }

    private int categorize(String tmdbId) {
        if(ImdbUtility.TVDB_TMDB_EPISODE.matcher(tmdbId).find())
            return 2;
        if(ImdbUtility.TVDB_TMDB_SEASON.matcher(tmdbId).find())
            return 1;
        if(ImdbUtility.TVDB_TMDB_SERIES.matcher(tmdbId).find())
            return 0;
        throw new IllegalArgumentException("This should never happen! Input was: " + tmdbId);
    }

    private boolean resolveUncached(ImdbMetadataResult toResolve, String tmdbId, int category) {
        switch(category) {
            case 0:
                return resolveSeries(tmdbId, toResolve);
            case 1:
                return resolveSeason(tmdbId, toResolve);
            case 2:
                return resolveEpisode(tmdbId, toResolve);
            default:
                throw new UnsupportedOperationException();
        }
    }
    
    private boolean resolveSeries(String tmdbId, ImdbMetadataResult toResolve) {
        String[] parts = tmdbId.split("/");
        var response = query(() -> api.seriesImdbId(parts[0]), tmdbId);
        if(!response.success) {
            Logger.warn("TMDB TV Show API failed for {}: {}", tmdbId, response.message);
            return false;
        }
        var data = gson.fromJson(response.message, Unmarshal.class);
        if(data == null || data.imdb_id == null || data.imdb_id.isBlank()) {
            Logger.warn("TMDB TV Show item {} with id {} does not have an IMDB id associated.", toResolve.title, tmdbId);
            blacklist.cache(tmdbId, "");
            return false;
        }
        cache.cache(tmdbId, data.imdb_id);
        toResolve.imdbId = data.imdb_id;
        return true;
    }
    
    private boolean resolveSeason(String tmdbId, ImdbMetadataResult toResolve) {
        // If ever added to IMDB could be implemented here
        return false;
    }
    
    private boolean resolveEpisode(String tmdbId, ImdbMetadataResult toResolve) {
        String parts[] = tmdbId.split("/");
        var response = query(() -> api.episodeImdbId(parts), tmdbId);
        if(!response.success) {
            Logger.warn("TMDB TV Show API failed for {}: {}", tmdbId, response.message);
            return false;
        }
        var data = gson.fromJson(response.message, Unmarshal.class);
        if(data == null || data.imdb_id == null || data.imdb_id.isBlank()) {
            Logger.warn("TMDB TV Show item {} with id {} does not have an IMDB id associated.", toResolve.title, tmdbId);
            blacklist.cache(tmdbId, "");
            return false;
        }
        cache.cache(tmdbId, data.imdb_id);
        toResolve.imdbId = data.imdb_id;
        return true;
    }
    
    private ApiResult query(Supplier<HttpResponse<String>> supplier, String tmdbId) {
        HttpResponse<String> response = null;
        Exception ex = null;
        
        for(int i = 0; i < MAX_TRIES; i++) {
            try {
                response = supplier.get();
                if(response.statusCode() == 200)
                    return new ApiResult(true, response.body());
                if(response.statusCode() == 404) {
                    blacklist.cache(tmdbId, "");
                    return new ApiResult(false, response.body());
                }
                Logger.warn("TMDB TV Show API returned a reply with status code != 200. Trying again... {}/{}", i+1, MAX_TRIES);
            } catch(Exception e) {
                ex = e;
                Logger.warn("TMDB TV Show API request failed: [" + (i+1) + "/" + MAX_TRIES +"] : " + e.getMessage());
                Logger.warn("Dumping response:" + response);
            }
        }
        
        if(ex != null)
            throw Utility.rethrow(ex);
        
        var error = gson.fromJson(response.body(), UnmarshalError.class);
        String msg = error.status_message == null ? "Unknown TMDB TV Show API error" + response.statusCode() : error.status_message + " | " + error.status_code + " | HTTP Code: " + response.statusCode();
        return new ApiResult(false, msg);
    }
}
