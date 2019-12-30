package updatetool.common;

import java.io.IOException;
import java.net.http.HttpResponse;

public class OmdbApi extends AbstractApi {

    public static class OMDBResponse {
        public final String Title, imdbRating, imdbID, Error;
        public final Boolean Response;
        private long created;

        public OMDBResponse(String title, String imdbRating, String imdbID, String error, boolean response) {
            Title = title;
            this.imdbRating = imdbRating;
            this.imdbID = imdbID;
            Error = error;
            Response = response;
        }

        public void touch() {
            created = System.currentTimeMillis();
        }

        public long created() {
            return created;
        }
    }

    public OmdbApi(String apiKey) {
        super(apiKey);
    }

    public HttpResponse<String> queryId(String imdbId) throws IOException, InterruptedException {
        return send(get(of(imdbId)));
    }

    public HttpResponse<String> testApi() throws IOException, InterruptedException {
        return send(get(of("tt3896198")));
    }

    private String of(String imdbId) {
        return String.format("http://www.omdbapi.com/?i=%s&apikey=%s", imdbId, apikey());
    }

    @Override
    public String keysWhere() {
        return "https://www.omdbapi.com/";
    }

}
