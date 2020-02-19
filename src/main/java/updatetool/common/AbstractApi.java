package updatetool.common;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

public abstract class AbstractApi {
    private final HttpClient client;

    public AbstractApi() {
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
    
    protected final HttpRequest postJson(String url, String jsonBody) {
        try {
            return HttpRequest.newBuilder(new URI(url))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(jsonBody))
                    .build();
        } catch(URISyntaxException e) {
            throw Utility.rethrow(e);
        }
    }

    protected HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, BodyHandlers.ofString());
    }

}
