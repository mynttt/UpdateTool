package imdbupdater.imdb;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.tinylog.Logger;
import com.google.gson.Gson;
import common.OmdbApi;
import common.OmdbApi.OMDBResponse;
import common.Utility;
import imdbupdater.Main;
import imdbupdater.exceptions.RatelimitException;
import imdbupdater.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

class ImdbOmdbWorker implements Callable<Void> {
    private static final Integer RETRY_BEFORE_FAILURE = 5;

    private final List<ImdbMetadataResult> sub;
    private final Gson gson;
    private final OmdbApi api;
    private final AtomicInteger counter;
    private final int n;
    private final ImdbOmdbCache cache;

    ImdbOmdbWorker(List<ImdbMetadataResult> sub, Gson gson, OmdbApi omdbApi, AtomicInteger counter, int n, ImdbOmdbCache cache) {
        this.sub = sub;
        this.gson = gson;
        this.api = omdbApi;
        this.counter = counter;
        this.n = n;
        this.cache = cache;
    }

    @Override
    public Void call() throws Exception {
        for(var item : sub) {
            if(cache.isCached(item.imdbId))
                continue;

            HttpResponse<String> response = null;

            for(int i = 0; i < RETRY_BEFORE_FAILURE; i++) {
                try {
                    response = api.queryId(item.imdbId);
                if(response.statusCode() == 200 || response.statusCode() == 401)
                    break;
                } catch(Exception e) {
                    Logger.warn("OMDB API request failed: [" + (i+1) + "/" + RETRY_BEFORE_FAILURE +"] : " + response.statusCode() + " -> " + response.body());
                    if(i == RETRY_BEFORE_FAILURE-1)
                        throw e;
                }
            }

            if(response.statusCode() == 401)
                throw new RatelimitException();

            OMDBResponse result = gson.fromJson(response.body(), OMDBResponse.class);
            result.touch();

            if(result.Response) {
                if(Main.PRINT_STATUS)
                    Utility.printStatusBar(counter.getAndIncrement(), n, 15);
                cache.cache(item.imdbId, result);
                continue;
            }

            throw new IllegalStateException("API call failed with code 200: " + response.body());
        }
        return null;
    }
}