package updatetool.imdb.resolvement;

import java.util.Map;
import java.util.Objects;
import org.tinylog.Logger;
import com.google.gson.Gson;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.HttpRunner;
import updatetool.common.HttpRunner.Converter;
import updatetool.common.HttpRunner.Handler;
import updatetool.common.HttpRunner.HttpCodeHandler;
import updatetool.common.HttpRunner.RunnerResult;
import updatetool.common.KeyValueStore;
import updatetool.common.TvdbApi;
import updatetool.common.TvdbApi.UnmarshalTvdb;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class TvdbToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private final KeyValueStore cache, blacklist;
    private final TvdbApi api;
    private final HttpRunner<String, UnmarshalTvdb, ImdbMetadataResult> runner;

    public TvdbToImdbResolvement(KeyValueStore cache, KeyValueStore blacklist, TvdbApi api) {
        this.cache = cache;
        this.blacklist = blacklist;
        this.api = api;
        
        var gson = new Gson();
        
        Converter<String, UnmarshalTvdb> converter = resp -> Objects.requireNonNull(gson.fromJson(resp.body(), UnmarshalTvdb.class));
        
        Handler<String, UnmarshalTvdb, ImdbMetadataResult> handler = (resp, res, payload) -> {
            if(res.Error != null) {
                Logger.error("TVDB item {} with id {} reported error: {}", payload.title, payload.extractedId, res.Error);
                blacklist.cache(payload.extractedId, "");
                return RunnerResult.ofSuccess(res);
            }
            
            String imdbId = res.getImdbId();
            
            if(imdbId != null && !imdbId.isBlank()) {
                cache.cache(payload.extractedId, imdbId);
                payload.imdbId = imdbId;
                payload.resolved = true;
            } else {
                blacklist.cache(payload.extractedId, "");
                Logger.warn("TVDB item {} with id {} does not have an IMDB id associated.", payload.title, payload.extractedId);
            }
            
            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, UnmarshalTvdb, ImdbMetadataResult> handler404 = (resp, res, payload) -> {
            blacklist.cache(payload.extractedId, "");
            return RunnerResult.ofSuccess(res);
        };
        
        this.runner = new HttpRunner<>(converter, HttpCodeHandler.of(Map.of(200, handler, 404, handler404)) ,"TVDB API", 3);
    }
    
    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        toResolve.extractedId = ImdbUtility.extractId(ImdbUtility.TVDB_TMDB_SERIES_MATCHING, toResolve.guid);
        
        if(toResolve.extractedId == null) {
            Logger.error("Item: {} is detected as TVDB but has no id. (guid={})", toResolve.title, toResolve.guid);
            return false;
        }
        
        var lookup = cache.lookup(toResolve.extractedId);
        if(lookup != null) {
            toResolve.imdbId = lookup;
            return true;
        }
        
        if(blacklist.lookup(toResolve.extractedId) != null)
            return false;
        
        String[] parts = toResolve.extractedId.split("/");
        
        switch(categorize(toResolve.extractedId)) {
            case 0:
                runner.run(() -> api.seriesImdbId(parts[0]), toResolve);
                break;
            case 1:
             // Seasons: If ever added to IMDB could be implemented here
                return false;
            case 2:
                runner.run(() -> api.episodeImdbId(parts), toResolve);
                break;
            default:
                throw new UnsupportedOperationException();
        }
        return toResolve.resolved;
    }
    
    private int categorize(String tvdbId) {
        if(ImdbUtility.TVDB_TMDB_EPISODE.matcher(tvdbId).find())
            return 2;
        if(ImdbUtility.TVDB_TMDB_SEASON.matcher(tvdbId).find())
            return 1;
        if(ImdbUtility.TVDB_TMDB_SERIES.matcher(tvdbId).find())
            return 0;
        throw new IllegalArgumentException("This should never happen! Input was: " + tvdbId);
    }
}
