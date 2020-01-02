package updatetool.imdb;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.tinylog.Logger;
import com.google.gson.Gson;
import updatetool.common.OmdbApi;
import updatetool.common.OmdbApi.OMDBResponse;
import updatetool.exceptions.ApiCallFailedException;
import updatetool.exceptions.RatelimitException;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

class ImdbOmdbWorker implements Callable<Void> {
    private static final int RETRY_BEFORE_FAILURE = 5;
    private static final int WAIT_FOR_N_MILLIS_IF_FAIL = 3000;

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
            if(cache.isOmdbResponseCached(item.imdbId))
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
                        if(result.Response)
                            break;
                        Logger.warn("OMDB API returned a reply with status code 200 but the reply is invalid. Waiting {} ms and trying again... {}/{}", i+1, WAIT_FOR_N_MILLIS_IF_FAIL, RETRY_BEFORE_FAILURE);
                    }
                    Logger.warn("OMDB API returned a reply with status code != 200. Waiting {} ms and trying again... {}/{}", i+1, WAIT_FOR_N_MILLIS_IF_FAIL, RETRY_BEFORE_FAILURE);
                } catch(Exception e) {
                    Logger.warn("OMDB API request failed: Waiting {} ms and trying again... [{}/{}] : {} -> {}", WAIT_FOR_N_MILLIS_IF_FAIL, i+1, RETRY_BEFORE_FAILURE, e.getClass().getSimpleName(), e.getMessage());
                    if(i == RETRY_BEFORE_FAILURE-1)
                        throw e;
                }
                Thread.sleep(WAIT_FOR_N_MILLIS_IF_FAIL);
            }

            if(response.statusCode() == 401)
                throw new RatelimitException();

            if(response.statusCode() != 200 || !result.Response)
                throw new ApiCallFailedException("API call failed with code " + response.statusCode() + " after + " + RETRY_BEFORE_FAILURE + " attempt(s): " + response.body());

            int c = counter.incrementAndGet();
            if(c % 100 == 0)
                Logger.info("Fetching [{}/{}]...", c, n);

            result.touch();
            cache.cacheOmdbResponse(item.imdbId, result);
        }
        return null;
    }
}
