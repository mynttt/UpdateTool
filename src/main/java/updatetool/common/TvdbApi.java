package updatetool.common;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import com.google.gson.Gson;

public class TvdbApi extends AbstractApi {
    private static final String BASE_URL = "https://api.thetvdb.com";
    private final String authToken;
    private final Gson gson = new Gson();

    public TvdbApi(String[] credentials) throws IllegalArgumentException {
        super();
        authToken = "Bearer " + auth(credentials);
    }

    private class Token { String token; };
    
    private String auth(String[] credentials) {
        try {
            var response = send(
                        postJson(BASE_URL + "/login", gson.toJson(Map.of(
                                "username", credentials[0],
                                "userkey", credentials[1],
                                "apikey", credentials[2])
                                ))
                        );
            if(response.statusCode() != 200)
                throw new IllegalArgumentException("Code " + response.statusCode() + " | " + response.body());
            return new Gson().fromJson(response.body(), Token.class).token;
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }
    
    public HttpResponse<String> seriesImdbId(String tvdbId) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/series/%s", BASE_URL, tvdbId)))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }
    
    public HttpResponse<String> episodeImdbId(String[] parts) {
        try {
            return send(HttpRequest.newBuilder(new URI(String.format("%s/series/%s/episodes/query?airedSeason=%s&airedEpisode=%s", BASE_URL, parts[0], parts[1], parts[2])))
                    .GET()
                    .header("Authorization", authToken)
                    .build());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }
    
    @Override
    public HttpResponse<String> testApi() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String keysWhere() {
        throw new UnsupportedOperationException();
    }

}
