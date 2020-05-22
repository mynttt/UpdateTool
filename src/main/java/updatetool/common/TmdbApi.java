package updatetool.common;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class TmdbApi extends AbstractApi {
    
    private final String apiKey;

    public static class UnmarshalMovie {
        public final String imdb_id = null, title = null;
    }
    
    public static class UnmarshalSeries {
        public final String imdb_id = null, status_message = null;
        public final int status_code = 0;
    }
    
    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    public static class UnmarshalFindByImdb {
        public final ArrayList<UnmarshalImdbHit> movie_results = null;
        public final int status_code = 0;
        public final String status_message = null;
        
        public static class UnmarshalImdbHit {
            public final String id = null;
        }
    }
    
    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    public static class UnmarshalCertifications {
        public final Map<String, List<UnmarshalCertificationsItem>> certifications = null;
        public final int status_code = 0;
        public final String status_message = null;
        
        public static class UnmarshalCertificationsItem {
            public final String certification = null, order = null;
        }
    }
    
    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    public static class UnmarshalMovieCertificationsQuery {
        public final int status_code = 0;
        public final String status_message = null;
        public final List<UnmarshalMovieCertificationsQueryItem> results = null;
        
        public static class UnmarshalMovieCertificationsQueryItem {
            public final String iso_3166_1 = null;
            public final List<UnmarshalMovieCertificationsQueryRelease> release_dates = null;
            
        }
        
        public static class UnmarshalMovieCertificationsQueryRelease {
            public final String certification = null;
        }
    }
    
    public static class UnmarshalTvCertifications {
        public final List<UnmarshalCertification> results = null;
        
        public static class UnmarshalCertification {
            public final String iso_3166_1 = null, rating = null;
        }
    }

    public TmdbApi(String apiKey) {
        this.apiKey = apiKey;
    }

    public HttpResponse<String> supportedMovieCertifications() throws IOException, InterruptedException {
        return send(get(String.format("https://api.themoviedb.org/3/certification/movie/list?api_key=%s", apiKey)));
    }
    
    public HttpResponse<String> tmdbId2imdbId(String tmdbId) throws IOException, InterruptedException {
        return send(get(String.format("https://api.themoviedb.org/3/movie/%s?api_key=%s", tmdbId, apiKey)));
    }
    
    public HttpResponse<String> imdbId2tmdbId(String imdbId) throws IOException, InterruptedException {
        return send(get(String.format("https://api.themoviedb.org/3/find/%s?api_key=%s&external_source=imdb_id", imdbId, apiKey)));
    }

    public HttpResponse<String> testApi() throws IOException, InterruptedException {
        return tmdbId2imdbId("550");
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
    
    public HttpResponse<String> movieContentRatingsFromTmdbId(String tmdb) {
        try {
            return send(get(String.format("https://api.themoviedb.org/3/movie/%s/release_dates?api_key=%s", tmdb, apiKey)));
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }
    
    public HttpResponse<String> tvContentRatingsFromTmdbSeriesId(String seriesId) {
        try {
            return send(get(String.format("https://api.themoviedb.org/3/tv/%s/content_ratings?api_key=%s", seriesId, apiKey)));
        } catch (IOException | InterruptedException e) {
            throw Utility.rethrow(e);
        }
    }

    @Override
    public String keysWhere() {
        return "https://www.themoviedb.org";
    }
}
