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
import updatetool.common.TmdbApi;
import updatetool.common.TmdbApi.UnmarshalSeries;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class TmdbSeriesToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private final KeyValueStore cache, blacklist;
    private final TmdbApi api;
    private final HttpRunner<String, UnmarshalSeries, ImdbMetadataResult> runner;
    
    public TmdbSeriesToImdbResolvement(KeyValueStore cache, KeyValueStore blacklist, TmdbApi api) {
        this.cache = cache;
        this.api = api;
        this.blacklist = blacklist;
        Gson gson = new Gson();
        
        Converter<String, UnmarshalSeries> converter = res -> Objects.requireNonNull(gson.fromJson(res.body(), UnmarshalSeries.class));
        
        Handler<String, UnmarshalSeries, ImdbMetadataResult> handler = (resp, res, payload) -> {
            if(res.imdb_id == null || res.imdb_id.isBlank()) {
                Logger.warn("TMDB TV Show item {} with id {} does not have an IMDB id associated. [{}]", payload.title, payload.extractedId, res.status_message);
                blacklist.cache(payload.extractedId, "");
                return RunnerResult.ofSuccess(res);
            }
            cache.cache(payload.extractedId, res.imdb_id);
            payload.imdbId = res.imdb_id;
            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, UnmarshalSeries, ImdbMetadataResult> handler404 = (resp, res, payload) -> {
            blacklist.cache(payload.extractedId, "");
            return RunnerResult.ofSuccess(res);
        };
        
        this.runner = new HttpRunner<>(converter, HttpCodeHandler.of(Map.of(
                    200, handler, 404, handler404)) ,"TMDB API (TV Show)", 3);
    }

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        toResolve.extractedId = ImdbUtility.extractId(ImdbUtility.TVDB_TMDB_SERIES_MATCHING, toResolve.guid);
        
        if(toResolve.extractedId == null) {
            Logger.warn("Item: {} is detected as TMDB TV Show but has no id. (guid={})", toResolve.title, toResolve.guid);
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

    private int categorize(String tmdbId) {
        if(ImdbUtility.TVDB_TMDB_EPISODE.matcher(tmdbId).find())
            return 2;
        if(ImdbUtility.TVDB_TMDB_SEASON.matcher(tmdbId).find())
            return 1;
        if(ImdbUtility.TVDB_TMDB_SERIES.matcher(tmdbId).find())
            return 0;
        throw new IllegalArgumentException("This should never happen! Input was: " + tmdbId);
    }
}