package updatetool.common;

import java.io.IOException;
import java.net.http.HttpResponse;

public class TmdbApi extends AbstractApi {
    private final String apiKey;

    public static class TMDBResponse {
        public final String imdb_id, title;

        public TMDBResponse(String imdb_id, String title) {
            this.imdb_id = imdb_id;
            this.title = title;
        }
    }

    public TmdbApi(String apiKey) {
        super();
        this.apiKey = apiKey;
    }

    public HttpResponse<String> tmdbId2imdbId(String tmdbId) throws IOException, InterruptedException {
        return send(get(of(tmdbId)));
    }

    public HttpResponse<String> queryForId(String id) throws IOException, InterruptedException {
        return send(get(of(id)));
    }

    public HttpResponse<String> testApi() throws IOException, InterruptedException {
        return send(get(of("550")));
    }

    private String of(String tmdbId) {
        return String.format("https://api.themoviedb.org/3/movie/%s?api_key=%s", tmdbId, apiKey);
    }

    @Override
    public String keysWhere() {
        return "https://www.themoviedb.org";
    }

    public HttpResponse<String> seriesImdbId(String seriesId) {
        try {
            return send(get(String.format("https://api.themoviedb.org/3/tv/%s/external_ids?api_key=%s", seriesId, apiKey)));
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }

    public HttpResponse<String> episodeImdbId(String[] parts) {
        try {
            return send(get(String.format("https://api.themoviedb.org/3/tv/%s/season/%s/episode/%s/external_ids?api_key=%s", parts[0], parts[1], parts[2], apiKey)));
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }

}
