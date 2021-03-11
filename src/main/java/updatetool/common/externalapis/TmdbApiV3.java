package updatetool.common.externalapis;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import org.tinylog.Logger;
import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import updatetool.common.HttpRunner;
import updatetool.common.KeyValueStore;
import updatetool.common.Utility;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.common.HttpRunner.Converter;
import updatetool.common.HttpRunner.Handler;
import updatetool.common.HttpRunner.HttpCodeHandler;
import updatetool.common.HttpRunner.RunnerResult;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class TmdbApiV3 extends AbstractApi implements TmdbApi {
    private final String apiKey;
    private final KeyValueStore cacheTv, cacheMovie, blacklistTv, blacklistMovie;
    private final HttpRunner<String, UnmarshalMovie, ImdbMetadataResult> runnerMovie;
    private final HttpRunner<String, UnmarshalSeries, ImdbMetadataResult> runnerSeries;

    @SuppressFBWarnings("DM_EXIT")
    public TmdbApiV3(String apiKey, KeyValueStore cacheTv, KeyValueStore cacheMovie, KeyValueStore blacklistTv, KeyValueStore blacklistMovie) {
        this.apiKey = apiKey;
        this.cacheTv = cacheTv;
        this.cacheMovie = cacheMovie;
        this.blacklistTv = blacklistTv;
        this.blacklistMovie = blacklistMovie;
        
        Logger.info("Testing TMDB API (v3) key: " + apiKey);
        try {
            var response = tmdbId2imdbId("550");
            if(response.statusCode() != 200) {
                Logger.error("API Test failed: Code " + response.statusCode());
                Logger.error("Payload:" + response.body());
                Logger.error("Key available under: https://www.themoviedb.org");
                System.exit(-1);
            }
        } catch(Exception e) {
            throw Utility.rethrow(e);
        }
        Logger.info("Test passed. API Key is valid.");
        
        var gson = new Gson();
        Converter<String, UnmarshalMovie> converterMovie = resp -> Objects.requireNonNull(
                gson.fromJson(resp.body(), UnmarshalMovie.class), 
                "TMDB API (v3) returned null response object. API broken or changed?");
        
        Handler<String, UnmarshalMovie, ImdbMetadataResult> handlerMovie = (resp, res, payload) -> {
            if(res.imdb_id == null || res.imdb_id.isBlank()) {
                Logger.warn("TMDB Movie item (v3) {} with id {} does not have an IMDB id associated.", res.title, payload.extractedId);
                blacklistMovie.cache(payload.extractedId, "");
                return RunnerResult.ofSuccess(res);
            }

            cacheMovie.cache(payload.extractedId, res.imdb_id);
            payload.imdbId = res.imdb_id;
            payload.resolved = true;

            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, UnmarshalMovie, ImdbMetadataResult> handler404movie = (resp, res, payload) -> {
            blacklistMovie.cache(payload.extractedId, "");
            return RunnerResult.ofSuccess(res);
        };
        
        this.runnerMovie = new HttpRunner<>(converterMovie,
                HttpCodeHandler.of(Map.of(200, handlerMovie, 404, handler404movie), handlerMovie),
                "TMDB API (Movie) (v3)",
                3);
        
        Converter<String, UnmarshalSeries> converterSeries = res -> Objects.requireNonNull(gson.fromJson(res.body(), UnmarshalSeries.class));
        
        Handler<String, UnmarshalSeries, ImdbMetadataResult> handlerSeries = (resp, res, payload) -> {
            if(res.imdb_id == null || res.imdb_id.isBlank()) {
                Logger.warn("TMDB TV Show item (v3) {} with id {} does not have an IMDB id associated. [{}]", payload.title, payload.extractedId, res.status_message);
                blacklistTv.cache(payload.extractedId, "");
                return RunnerResult.ofSuccess(res);
            }
            cacheTv.cache(payload.extractedId, res.imdb_id);
            payload.imdbId = res.imdb_id;
            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, UnmarshalSeries, ImdbMetadataResult> handler404 = (resp, res, payload) -> {
            blacklistTv.cache(payload.extractedId, "");
            return RunnerResult.ofSuccess(res);
        };
        
        this.runnerSeries = new HttpRunner<>(converterSeries, HttpCodeHandler.of(Map.of(
                    200, handlerSeries, 404, handler404)) ,"TMDB API (TV Show) (v3)", 3);
    }
    
    private static class UnmarshalMovie {
        public final String imdb_id = null, title = null;
    }
    
    private static class UnmarshalSeries {
        public final String imdb_id = null, status_message = null;
    }
        
    private HttpResponse<String> tmdbId2imdbId(String tmdbId) throws IOException, InterruptedException {
        return send(get(String.format("https://api.themoviedb.org/3/movie/%s?api_key=%s", tmdbId, apiKey)));
    }
    
    private HttpResponse<String> seriesImdbId(String seriesId) {
        try {
            return send(get(String.format("https://api.themoviedb.org/3/tv/%s/external_ids?api_key=%s", seriesId, apiKey)));
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }

    private HttpResponse<String> episodeImdbId(String[] parts) {
        try {
            return send(get(String.format("https://api.themoviedb.org/3/tv/%s/season/%s/episode/%s/external_ids?api_key=%s", parts[0], parts[1], parts[2], apiKey)));
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }
    
    @Override
    public void resolveImdbIdForItem(ImdbMetadataResult result) {
        if(result.type == LibraryType.MOVIE) {
            result.extractedId = ImdbUtility.extractId(ImdbUtility.NUMERIC, result.guid);
            if(result.extractedId == null) {
                Logger.error("Item: {} is detected as TMDB (v3) but has no id. (guid={})", result.title, result.guid);
                return;
            }
            
            var lookup = cacheMovie.lookup(result.extractedId);
            if(lookup != null) {
                result.imdbId = lookup;
                result.resolved = true;
                return;
            }
            
            if(blacklistMovie.lookup(result.extractedId) != null)
                return;
            
            runnerMovie.run(() -> tmdbId2imdbId(result.extractedId), result);
        } else {
            result.extractedId = ImdbUtility.extractId(ImdbUtility.TVDB_TMDB_SERIES_MATCHING, result.guid);
            if(result.extractedId == null) {
                Logger.warn("Item: {} is detected as TMDB TV Show (v3) but has no id. (guid={})", result.title, result.guid);
                return;
            }
            
            var lookup = cacheTv.lookup(result.extractedId);
            if(lookup != null) {
                result.imdbId = lookup;
                result.resolved = true;
                return;
            }
            
            if(blacklistTv.lookup(result.extractedId) != null)
                return;
            
            String[] parts = result.extractedId.split("/");
            switch(categorize(result.extractedId)) {
                case 0:
                    runnerSeries.run(() -> seriesImdbId(parts[0]), result);
                    break;
                case 1:
                    // Seasons: If ever added to IMDB could be implemented here
                    return;
                case 2:
                    runnerSeries.run(() -> episodeImdbId(parts), result);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
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

    @Override
    public ApiVersion version() {
        return ApiVersion.TMDB_V3;
    }
}
