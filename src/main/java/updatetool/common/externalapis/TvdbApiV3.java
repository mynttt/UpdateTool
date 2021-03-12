package updatetool.common.externalapis;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import org.tinylog.Logger;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minidev.json.JSONArray;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.common.HttpRunner;
import updatetool.common.KeyValueStore;
import updatetool.common.Utility;
import updatetool.common.HttpRunner.Converter;
import updatetool.common.HttpRunner.Handler;
import updatetool.common.HttpRunner.HttpCodeHandler;
import updatetool.common.HttpRunner.RunnerResult;
import updatetool.exceptions.ApiCallFailedException;
import updatetool.imdb.ImdbUtility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class TvdbApiV3 extends AbstractApi implements TvdbApi {
    private static final ParseContext CTX = JsonPath.using(Configuration.defaultConfiguration().setOptions(Option.SUPPRESS_EXCEPTIONS));
    private static final String BASE_URL = "https://api.thetvdb.com";
    private String authToken;
    private final Gson gson = new Gson();
    private final KeyValueStore cache, blacklist, cacheMovie, blacklistMovie;
    private final HttpRunner<String, UnmarshalTvdb, ImdbMetadataResult> runner;
    private final HttpRunner<String, String, ImdbMetadataResult> runnerMovie;

    private class UnmarshalTvdb {
        public final String Error = null;
        private final Object data = null;
        
        private boolean isSeries() {
            return data instanceof LinkedTreeMap;
        }
        
        private boolean isEpisode() {
            return data instanceof ArrayList;
        }
        
        @SuppressWarnings("rawtypes")
        private String getImdbId() {
            if(data == null) return null;
            if(isSeries()) return (String) ((LinkedTreeMap) data).get("imdbId");
            if(isEpisode()) return (String) ((LinkedTreeMap) ((ArrayList) data).get(0)).get("imdbId");
            return null;
        }
    }
    
    private class Token { String token; };
    
    @SuppressFBWarnings("DM_EXIT")
    public TvdbApiV3(String key, KeyValueStore blacklist, KeyValueStore cache, KeyValueStore cacheMovie, KeyValueStore blacklistMovie) throws ApiCallFailedException {
        Logger.info("Testing TVDB API (v3) authorization apikey: {}", key);
        
        try {
            authToken = "Bearer " + auth(key);
        } catch(ApiCallFailedException e) {
            Logger.error("API Test failed: " + e.getMessage());
            Logger.error("Legacy (v3) keys available under: https://thetvdb.com/");
            System.exit(-1);
        }
        
        Logger.info("Test passed. API Key is valid.");
        this.cache = cache;
        this.blacklist = blacklist;
        this.blacklistMovie = blacklistMovie;
        this.cacheMovie = cacheMovie;
        
        Converter<String, UnmarshalTvdb> converter = resp -> Objects.requireNonNull(gson.fromJson(resp.body(), UnmarshalTvdb.class));
        
        Handler<String, UnmarshalTvdb, ImdbMetadataResult> handler = (resp, res, payload) -> {
            if(res.Error != null) {
                Logger.error("TVDB item (v3) {} with id {} reported error: {}", payload.title, payload.extractedId, res.Error);
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
                Logger.warn("TVDB item (v3) {} with id {} does not have an IMDB id associated.", payload.title, payload.extractedId);
            }
            
            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, UnmarshalTvdb, ImdbMetadataResult> handler404 = (resp, res, payload) -> {
            blacklist.cache(payload.extractedId, "");
            return RunnerResult.ofSuccess(res);
        };
        
        Converter<String, String> converterMovie = resp -> null;
        
        Handler<String, String, ImdbMetadataResult> handlerMovie = (resp, res, payload) -> {
            var doc = CTX.parse(resp.body());
            String error = doc.read("$.Error");
            if(error != null) {
                Logger.error("TVDB movie item (v3) {} with id {} reported error: {}", payload.title, payload.extractedId, error);
                blacklistMovie.cache(payload.extractedId, "");
                return RunnerResult.ofSuccess(res);
            }
            
            String imdbId;
            try {
                imdbId = (String) ((JSONArray) doc.read("$..remoteids[?(@.source_id == 2)].id")).get(0);
            } catch(Exception e) {
                return RunnerResult.ofSuccess(res);
            }
            
            if(imdbId != null && !imdbId.isBlank()) {
                cacheMovie.cache(payload.extractedId, imdbId);
                payload.imdbId = imdbId;
                payload.resolved = true;
            } else {
                blacklistMovie.cache(payload.extractedId, "");
                Logger.warn("TVDB movie item (v3) {} with id {} does not have an IMDB id associated.", payload.title, payload.extractedId);
            }
            
            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, String, ImdbMetadataResult> handler404m = (resp, res, payload) -> {
            blacklistMovie.cache(payload.extractedId, "");
            return RunnerResult.ofSuccess(res);
        };
        
        this.runner = new HttpRunner<>(converter, HttpCodeHandler.of(Map.of(200, handler, 404, handler404)) ,"TVDB API v3", 3);
        this.runnerMovie = new HttpRunner<>(converterMovie, HttpCodeHandler.of(Map.of(200, handlerMovie, 404, handler404m)), "TVDB API v3 (Movie)", 3);
    }

    private String auth(String key) throws ApiCallFailedException {
        try {
            var response = send(
                        postJson(BASE_URL + "/login", gson.toJson(Map.of(
                                "apikey", key)
                                ))
                        );
            if(response.statusCode() != 200) {
                Logger.error("TVDB authorization failed with code {}", response.statusCode());
                Logger.error("This could be due to the TVDB API having issues at the moment or your credentials being wrong.");
                Logger.error("This is the received response:");
                Logger.error(response.body());
                Logger.error("===================================================");
                throw new ApiCallFailedException("TVDB API authorization failed.");
            }
            return new Gson().fromJson(response.body(), Token.class).token;
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }
    
    private HttpResponse<String> movieImdbId(String tvdbId) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/movies/%s", BASE_URL, tvdbId)))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }
    
    private HttpResponse<String> seriesImdbId(String tvdbId) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/series/%s", BASE_URL, tvdbId)))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }
    
    private HttpResponse<String> episodeImdbId(String[] parts) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/series/%s/episodes/query?airedSeason=%s&airedEpisode=%s", BASE_URL, parts[0], parts[1], parts[2])))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
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

    @Override
    public void resolveImdbIdForItem(ImdbMetadataResult result) {
        if(result.type == LibraryType.MOVIE) {
            result.extractedId = ImdbUtility.extractId(ImdbUtility.TVDB_TMDB_SERIES, result.guid);
            
            if(result.extractedId == null) {
                Logger.error("Item: {} is detected as TVDB Movie (v3) but has no id. (guid={})", result.title, result.guid);
                return;
            }
            
            var lookup = cacheMovie.lookup(result.extractedId);
            if(lookup != null) {
                result.imdbId = lookup;
                result.resolved = true;
                return;
            }
            
            if(blacklistMovie.lookup(result.extractedId) != null) {
                return;
            }
            
            runnerMovie.run(() -> movieImdbId(result.extractedId), result);
        } else {
            result.extractedId = ImdbUtility.extractId(ImdbUtility.TVDB_TMDB_SERIES_MATCHING, result.guid);
            
            if(result.extractedId == null) {
                Logger.error("Item: {} is detected as TVDB (v3) but has no id. (guid={})", result.title, result.guid);
                return;
            }
            
            var lookup = cache.lookup(result.extractedId);
            if(lookup != null) {
                result.imdbId = lookup;
                result.resolved = true;
                return;
            }
            
            if(blacklist.lookup(result.extractedId) != null) {
                return;
            }
            
            String[] parts = result.extractedId.split("/");
            
            switch(categorize(result.extractedId)) {
                case 0:
                    runner.run(() -> seriesImdbId(parts[0]), result);
                    break;
                case 1:
                 // Seasons: If ever added to IMDB could be implemented here
                    result.resolved = false;
                    return;
                case 2:
                    runner.run(() -> episodeImdbId(parts), result);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public ApiVersion version() {
        return ApiVersion.TVDB_V3;
    }
}
