package updatetool.imdb.resolvement;

import java.net.http.HttpResponse;
import java.util.Objects;
import org.tinylog.Logger;
import com.google.gson.Gson;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.KeyValueStore;
import updatetool.common.TmdbApi;
import updatetool.common.Utility;
import updatetool.common.TmdbApi.TMDBResponse;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class TmdbToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private static final int MAX_TRIES = 3;
    private final Gson gson;
    private final KeyValueStore cache;
    private final TmdbApi api;
    
    public TmdbToImdbResolvement(KeyValueStore cache, TmdbApi api) {
        this.cache = cache;
        this.api = api;
        this.gson = new Gson();
    }

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        String tmdbId = ImdbUtility.extractId(ImdbUtility.NUMERIC, toResolve.guid);
        if(tmdbId == null) {
            Logger.error("Item: {} is detected as TMDB but has no id. (guid={})", toResolve.title, toResolve.guid);
            return false;
        }
        var lookup = cache.lookup(tmdbId);
        if(lookup != null) {
            toResolve.imdbId = lookup;
            return true;
        }
        return resolveUncached(toResolve, tmdbId);
    }

    public boolean resolveUncached(ImdbMetadataResult toResolve, String tmdbId) {
        Logger.info("Attempting to resolve TMDB identifer {} against IMDB...", tmdbId);
        Exception ex = null;
        TMDBResponse result = null;
        HttpResponse<String> response = null;

        for(int i = 0; i < MAX_TRIES; i++) {
            try {
                response = api.queryForId(tmdbId);
                if(response.statusCode() == 200) {
                    result = gson.fromJson(response.body(), TMDBResponse.class);
                    Objects.requireNonNull(result, "TMDB API returned null response object. API broken or changed?");
                    break;
                }
                Logger.warn("TMBD API returned a reply with status code != 200. Trying again... {}/{}", i+1, MAX_TRIES);
            } catch(Exception e) {
                Logger.warn("TMBD API request failed: [" + (i+1) + "/" + MAX_TRIES +"] : " + e.getMessage());
                Logger.warn("Dumping response:" + response);
                ex = e;
            }
        }
        
        if(ex != null)
            throw Utility.rethrow(ex);
        
        if(result == null) {
            Logger.warn("TMDB API failed to deliver a valid response. Dumping last response: {}", response);
            return false;
        }

        if(result.imdb_id == null || result.imdb_id.isBlank()) {
            Logger.warn("TMDB item {} with id {} does not have an IMDB id associated.", result.title, tmdbId);
            return false;
        }

        cache.cache(tmdbId, result.imdb_id);
        toResolve.imdbId = result.imdb_id;

        Logger.info("Resolved and cached TMDB {} to IMDB {}.", tmdbId, result.imdb_id);
        return true;
    }

}
