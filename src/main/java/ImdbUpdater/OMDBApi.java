package ImdbUpdater;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Objects;

public class OMDBApi {
    private final String apiKey;
    private final HttpClient client;

    public static class OMDBResponse {
        public final String Title, imdbRating, imdbID, Error;
        public final boolean Response;

        public OMDBResponse(String title, String imdbRating, String imdbID, String error, boolean response) {
            Title = title;
            this.imdbRating = imdbRating;
            this.imdbID = imdbID;
            Error = error;
            Response = response;
        }
    }

    public OMDBApi(String apiKey) {
        Objects.requireNonNull(apiKey);
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .version(Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(2000))
                .build();
    }

    public HttpResponse<String> queryId(String imdbId) throws IOException, InterruptedException {
        return client.send(ofId(imdbId), BodyHandlers.ofString());
    }

    public HttpResponse<String> testApi() throws IOException, InterruptedException {
        return client.send(ofId("tt3896198"), BodyHandlers.ofString());
    }

    private HttpRequest ofId(String imdbId) {
        try {
            return HttpRequest.newBuilder(new URI(String.format("http://www.omdbapi.com/?i=%s&apikey=%s", imdbId, apiKey)))
                .GET()
                .build();
        } catch(URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }

}
