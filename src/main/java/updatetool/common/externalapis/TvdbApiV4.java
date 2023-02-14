package updatetool.common.externalapis;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.tinylog.Logger;
import com.google.gson.Gson;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.common.DatabaseSupport.NewAgentSeriesType;
import updatetool.common.HttpRunner;
import updatetool.common.KeyValueStore;
import updatetool.common.Utility;
import updatetool.common.HttpRunner.Converter;
import updatetool.common.HttpRunner.Handler;
import updatetool.common.HttpRunner.HttpCodeHandler;
import updatetool.common.HttpRunner.RunnerResult;
import updatetool.imdb.ImdbUtility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

@SuppressWarnings("unused")
public class TvdbApiV4 extends AbstractApi implements TvdbApi {
    private static final Configuration CONFIG = Configuration.defaultConfiguration().setOptions(Option.SUPPRESS_EXCEPTIONS);
    private static final ParseContext CTX = JsonPath.using(CONFIG);
    private static final String APPLICATION_KEY = "17e55156-4eff-4a8b-950f-96f805e15878";
    private static final String BASE_URL = "https://api4.thetvdb.com/v4";
    private static final String JSON_PATH = "";
    private final HttpRunner<String, String, ImdbMetadataResult> runner;
    private final HttpRunner<String, String, ImdbMetadataResult> legacySeriesRunner;
    private final HttpRunner<String, String, ImdbMetadataResult> legacySeasonsRunner;
    private final String authToken;
    private final KeyValueStore cache, blacklist, cacheMovie, blacklistMovie, legacyMapping;

    public TvdbApiV4(String pin, KeyValueStore cache, KeyValueStore blacklist, KeyValueStore cacheMovie, KeyValueStore blacklistMovie, KeyValueStore legacyMapping) {
        authToken = "Bearer " + auth(pin);
        this.cache = cache;
        this.blacklist = blacklist;
        this.cacheMovie = cacheMovie;
        this.blacklistMovie = blacklistMovie;
        this.legacyMapping = legacyMapping;
        
        Converter<String, String> converter = resp -> null;
        
        Handler<String, String, ImdbMetadataResult> handler = (resp, res, payload) -> {
            var doc = CTX.parse(resp.body());
            String status = doc.read("$.status");
            if(!"success".equals(status)) {
                String msg = doc.read("$.message");
                Logger.error("TVDB {} item (v4) {} with id {} reported error: {}", payload.type, payload.title, payload.extractedId, msg);
                if(payload.type == LibraryType.MOVIE) {
                    blacklistMovie.cache(payload.extractedId, "");
                } else {
                    blacklist.cache(payload.extractedId, "");
                }
                return RunnerResult.ofSuccess(res);
            }
            
            String imdbId;
            try {
                imdbId = (String) ((JSONArray) doc.read("$..remoteIds[?(@.type == 2)].id")).get(0);
            } catch(Exception e) {
                if(payload.type == LibraryType.MOVIE) {
                    blacklistMovie.cache(payload.extractedId, "");
                } else {
                    blacklist.cache(payload.extractedId, "");
                }
                return RunnerResult.ofSuccess(res);
            }
            
            if(imdbId != null && !imdbId.isBlank()) {
                if(payload.type == LibraryType.MOVIE) {
                    cacheMovie.cache(payload.extractedId, imdbId);
                } else {
                    cache.cache(payload.extractedId, imdbId);
                }
                payload.imdbId = imdbId;
                payload.resolved = true;
            } else {
                if(payload.type == LibraryType.MOVIE) {
                    blacklistMovie.cache(payload.extractedId, "");
                } else {
                    blacklist.cache(payload.extractedId, "");
                }
                Logger.warn("TVDB {} item (v4) {} with id {} does not have an IMDB id associated.", payload.type, payload.title, payload.extractedId);
            }
            
            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, String, ImdbMetadataResult> handler404 = (resp, res, payload) -> {
            if(payload.type == LibraryType.MOVIE) {
                blacklistMovie.cache(payload.extractedId, "");
            } else {
                blacklist.cache(payload.extractedId, "");
            }
            return RunnerResult.ofSuccess(res);
        };
        
        this.runner = new HttpRunner<>(converter, HttpCodeHandler.of(Map.of(200, handler, 404, handler404)) ,"TVDB API v4 (Movie & TV Shows)", 3);
        
        /*
         * LEGACY
         * 
         */
        
        Handler<String, String, ImdbMetadataResult> handlerLegacy404 = (resp, res, payload) -> {
            blacklist.cache(payload.extractedId, "");
            return RunnerResult.ofSuccess(res);
        };
        
        Handler<String, String, ImdbMetadataResult> handlerLegacySeriesLookup = (resp, res, payload) -> {
            var doc = CTX.parse(resp.body());
            String status = doc.read("$.status");
            if(!"success".equals(status)) {
                String msg = doc.read("$.message");
                Logger.error("TVDB legacy lookup item (v3 -> v4)[SEASON] {} with id {} reported error: {}", payload.type, payload.title, payload.extractedId, msg);
                blacklist.cache(payload.extractedId, "");
                return RunnerResult.ofSuccess(res);
            }
            
            JSONArray arr = doc.read("$.data.seasons[?(@.name == \"Aired Order\")]['id','seriesId','number']");
            for(int i = 0; i < arr.size(); i++) {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Integer> o = (LinkedHashMap<String, Integer>) arr.get(i);
                legacyMapping.cache(o.get("seriesId") + "/" + o.get("number"), Integer.toString(o.get("id")));
            }
                        
            return RunnerResult.ofSuccess(res);
        };
        
        this.legacySeriesRunner = new HttpRunner<>(converter, HttpCodeHandler.of(Map.of(200, handlerLegacySeriesLookup, 404, handlerLegacy404)), "TVDB API v4 (Legacy Converter Season)", 3);
        
        Handler<String, String, ImdbMetadataResult> heandlerLegacySeasonLookup = (resp, res, payload) -> {
            var doc = CTX.parse(resp.body());
            String status = doc.read("$.status");
            if(!"success".equals(status)) {
                String msg = doc.read("$.message");
                Logger.error("TVDB legacy lookup item (v3 -> v4)[EPISODE] {} with id {} reported error: {}", payload.type, payload.title, payload.extractedId, msg);
                blacklist.cache(payload.extractedId, "");
                return RunnerResult.ofSuccess(res);
            }
            
            JSONArray arr = doc.read("$.data.episodes[*]['id','seriesId','seasonNumber','number']");
            for(int i = 0; i < arr.size(); i++) {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Integer> o = (LinkedHashMap<String, Integer>) arr.get(i);
                legacyMapping.cache(o.get("seriesId") + "/" + o.get("seasonNumber") + "/" + o.get("number"), Integer.toString(o.get("id")));
            }
                        
            return RunnerResult.ofSuccess(res);
        };
        
        this.legacySeasonsRunner = new HttpRunner<>(converter, HttpCodeHandler.of(Map.of(200, heandlerLegacySeasonLookup, 404, handlerLegacy404)), "TVDB API v4 (Legacy Converter Episode)", 3);;
    }
    
    @SuppressFBWarnings("DM_EXIT")
    private String auth(String pin) {
        Logger.info("Testing TMDB API (v4) pin: " + pin);
        try {
            var response = send(postJson(BASE_URL + "/login", new Gson().toJson(Map.of("apikey", APPLICATION_KEY, "pin", pin))));
            var status = CTX.parse(response.body()).read("$.status");
            if(response.statusCode() != 200 || !"success".equals(status)) {
                Logger.error("TVDB v4 authorization failed with code {} and status {}", response.statusCode(), status);
                Logger.error("This could be due to the TVDB API v4 having issues at the moment or your credentials being wrong.");
                Logger.error("This is the received response:");
                Logger.error("Payload:" + response.body());
                Logger.error("Pin (v4) available under: https://www.thetvdb.com");
                Logger.error("===================================================");
                System.exit(-1);
            }
            Logger.info("Test passed. API Key is valid.");
            return JsonPath.read(response.body(), "$.data.token");
        } catch (Exception e) {
            throw Utility.rethrow(e);
        }
    }

    private HttpResponse<String> queryForMovie(String id) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/movies/%s/extended", BASE_URL, id)))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }
    
    private HttpResponse<String> queryForSeries(String id) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/series/%s/extended?short=true", BASE_URL, id)))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }
    
    private HttpResponse<String> queryForSeasons(String id) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/seasons/%s/extended", BASE_URL, id)))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }  
    }
    
    private HttpResponse<String> queryForEpisode(String id) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/episodes/%s/extended", BASE_URL, id)))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }
    
    @Override
    public void resolveImdbIdForItem(ImdbMetadataResult result) {
        
        // Seasons are not supported atm by TVDB and IMDB
        if(result.guid.startsWith("plex://season"))
            return;
        
        result.extractedId = ImdbUtility.extractId(ImdbUtility.TVDB_TMDB_SERIES_MATCHING, result.guid);
        
        if(result.extractedId == null) {
            Logger.error("Item: {} is detected as TVDB (v3/v4) but has no id. (guid={})", result.title, result.guid);
            return;
        }
        
        String[] parts = result.extractedId.split("/");

        // Detect legacy seasons
        if(parts.length == 2)
            return;
        
        var cache = result.type == LibraryType.MOVIE ? cacheMovie : this.cache;
        var lookup = cache.lookup(result.extractedId);
        
        if(lookup != null) {
            result.imdbId = lookup;
            result.resolved = true;
            return;
        }
        
        var legacyLookup = legacyMapping.lookup(result.extractedId);
        if(legacyLookup != null) {
            result.imdbId = legacyLookup;
            result.resolved = true;
            return;
        }
        
        var blacklist = result.type == LibraryType.MOVIE ? blacklistMovie : this.blacklist;
        if(blacklist.lookup(result.extractedId) != null) {
            return;
        }
        
        // v3 series episode lookup
        if(parts.length == 3) {
            resolveLegacyLookup(parts, result);
        } else {
            runner.run(result.type == LibraryType.MOVIE ? () -> queryForMovie(result.extractedId) : result.seriesType == NewAgentSeriesType.EPISODE ? () -> queryForEpisode(result.extractedId) : () -> queryForSeries(result.extractedId), result);
        }
    }

    private void resolveLegacyLookup(String[] parts, ImdbMetadataResult result) {
        var lookup = legacyMapping.lookup(result.extractedId);
        
        if(lookup == null) {
          //Stage 1: Series
            legacySeriesRunner.run(() -> queryForSeries(parts[0]), result);
            var seasonLookup = legacyMapping.lookup(parts[0] + "/" + parts[1]);
            if(!lookupCheck(seasonLookup, result))
                return;
            
          //Stage 2: Season
            legacySeasonsRunner.run(() -> queryForSeasons(seasonLookup), result);
            var episodeLookup = legacyMapping.lookup(parts[0] + "/" + parts[1] + "/" + parts[2]);
            if(!lookupCheck(episodeLookup, result))
                return;
        }
        
        lookup = legacyMapping.lookup(result.extractedId);
        if(!lookupCheck(lookup, result))
            return;
        
        var lookupFinal = lookup;
        runner.run(() -> queryForEpisode(lookupFinal), result);
    }
    
    private boolean lookupCheck(String s, ImdbMetadataResult result) {
        if(s == null) {
            // 404 so just swallow
            blacklist.cache(result.extractedId, "");
            return false;
        }
        return true;
    }

    @Override
    public ApiVersion version() {
        return ApiVersion.TVDB_V4;
    }
    
}
