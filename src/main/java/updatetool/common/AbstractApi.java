package updatetool.common;

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

public abstract class AbstractApi {
    private final String apiKey;
    private final HttpClient client;

    public AbstractApi(String apiKey) {
        Objects.requireNonNull(apiKey);
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .version(Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(2000))
                .build();
    }

    public abstract HttpResponse<String> testApi() throws Exception;
    public abstract String keysWhere();

    protected final HttpRequest get(String url) {
        try {
            return HttpRequest.newBuilder(new URI(url))
                .GET()
                .build();
        } catch(URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }

    protected HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, BodyHandlers.ofString());
    }

    protected final String apikey() {
        return apiKey;
    }
}
