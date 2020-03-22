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
import updatetool.common.TmdbApi.UnmarshalMovie;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class TmdbMovieToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private final KeyValueStore cache;
    private final TmdbApi api;
    private final HttpRunner<String, UnmarshalMovie, ImdbMetadataResult> runner;
    
    public TmdbMovieToImdbResolvement(KeyValueStore cache, TmdbApi api) {
        this.cache = cache;
        this.api = api;
        
        var gson = new Gson();
        Converter<String, UnmarshalMovie> converter = resp -> Objects.requireNonNull(
                gson.fromJson(resp.body(), UnmarshalMovie.class), 
                "TMDB API returned null response object. API broken or changed?");
        
        Handler<String, UnmarshalMovie, ImdbMetadataResult> handler = (resp, res, payload) -> {
            if(res.imdb_id == null || res.imdb_id.isBlank()) {
                Logger.warn("TMDB Movie item {} with id {} does not have an IMDB id associated.", res.title, payload.extractedId);
                return RunnerResult.ofSuccess(res);
            }

            cache.cache(payload.extractedId, res.imdb_id);
            payload.imdbId = res.imdb_id;
            payload.resolved = true;

            return RunnerResult.ofSuccess(res);
        };
        
        this.runner = new HttpRunner<>(converter,
                HttpCodeHandler.of(Map.of(200, handler), handler),
                "TMDB API (Movie)",
                3);
    }

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        toResolve.extractedId = ImdbUtility.extractId(ImdbUtility.NUMERIC, toResolve.guid);
        if(toResolve.extractedId == null) {
            Logger.error("Item: {} is detected as TMDB but has no id. (guid={})", toResolve.title, toResolve.guid);
            return false;
        }
        var lookup = cache.lookup(toResolve.extractedId);
        if(lookup != null) {
            toResolve.imdbId = lookup;
            return true;
        }
        runner.run(() -> api.queryForId(toResolve.extractedId), toResolve);
        return toResolve.resolved;
    }
}
