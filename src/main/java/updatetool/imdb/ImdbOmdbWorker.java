package updatetool.imdb;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.tinylog.Logger;
import com.google.gson.Gson;
import updatetool.Main;
import updatetool.common.OmdbApi;
import updatetool.common.OmdbApi.OMDBResponse;
import updatetool.common.Utility;
import updatetool.exceptions.ApiCallFailedException;
import updatetool.exceptions.RatelimitException;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

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
            OMDBResponse result = null;

            for(int i = 0; i < RETRY_BEFORE_FAILURE; i++) {
                try {
                    response = api.queryId(item.imdbId);
                    if(response.statusCode() == 401)
                        break;
                    if(response.statusCode() == 200) {
                        result = gson.fromJson(response.body(), OMDBResponse.class);
                        if(result.Response == null)
                            throw new AssertionError("Response should not be null. API broken?");
                        if(!result.Response) {
                            Logger.warn("OMDB API returned a reply with status code 200 but the reply is invalid. Trying again... {}/{}", i+1, RETRY_BEFORE_FAILURE);
                            continue;
                        }
                        break;
                    }
                    Logger.warn("OMDB API returned a reply with status code != 200. Trying again... {}/{}", i+1, RETRY_BEFORE_FAILURE);
                } catch(Exception e) {
                    Logger.warn("OMDB API request failed: [" + (i+1) + "/" + RETRY_BEFORE_FAILURE +"] : " + e.getMessage());
                    Logger.warn("Dumping response:" + response);
                    if(i == RETRY_BEFORE_FAILURE-1)
                        throw e;
                }
            }

            if(response.statusCode() == 401)
                throw new RatelimitException();

            if(response.statusCode() != 200 || !result.Response)
                throw new ApiCallFailedException("API call failed with code " + response.statusCode() + " after + " + RETRY_BEFORE_FAILURE + " attempt(s): " + response.body());

            result.touch();
            if(Main.PRINT_STATUS)
                Utility.printStatusBar(counter.getAndIncrement(), n, 15);
            cache.cache(item.imdbId, result);
        }
        return null;
    }
}
