package updatetool.imdb;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.tinylog.Logger;
import updatetool.Main;
import updatetool.common.Capabilities;
import updatetool.common.KeyValueStore;

public class ImdbScraper implements Closeable {
    private static final int SCRAPE_EVERY_N_DAYS_IGNORE = 90;
    private static final int SCRAPE_EVERY_N_DAYS = 14;
    private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).version(Version.HTTP_2).connectTimeout(Duration.ofMillis(2000)).build();
    
    private final KeyValueStore idScrapeExpire = KeyValueStore.of(Main.PWD.resolve("imdbScrapeExpire.json")), 
                                idCachedValue = KeyValueStore.of(Main.PWD.resolve("imdbScrapeValue.json")),
                                id404Ignore = KeyValueStore.of(Main.PWD.resolve("imdbScrape404.json"));
    
    public String scrapeFallback(String imdbId, String title) throws Exception {
        String expired = idScrapeExpire.lookup(imdbId);
        
        if(id404Ignore.lookup(imdbId) != null) {
            return null;
        }
        
        if(expired == null || isExpired(expired)) {
            var result = scrape(imdbId);
            long cacheTime = SCRAPE_EVERY_N_DAYS_IGNORE;
            
            if(result == null) {
                idCachedValue.remove(imdbId);
            } else {
                cacheTime = SCRAPE_EVERY_N_DAYS;
                Logger.info("Scraped rating {} for id {} ({}). Cached for {} day(s)", result, imdbId, title, SCRAPE_EVERY_N_DAYS);
                idCachedValue.cache(imdbId, result);
            }
            
            idScrapeExpire.cache(imdbId, Long.toString(System.currentTimeMillis()+TimeUnit.DAYS.toMillis(cacheTime)));
            return result;
        } else {
            return idCachedValue.lookup(imdbId);
        }
    }
    
    private boolean isExpired(String expire) {
        long l = Long.parseLong(expire);
        return l <= System.currentTimeMillis();
    }
    
    private String scrape(String imdbId) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(new URI("https://www.imdb.com/title/"+imdbId))
                .header("User-Agent", "Mozilla/5.0 (X11; Linux i686; rv:88.0) Gecko/20100101 Firefox/88.0.")
                .GET()
                .build();
        
        var response = CLIENT.send(r, BodyHandlers.ofString());
        if(response.statusCode() != 200) {
            if(response.statusCode() == 404) {
                id404Ignore.cache(imdbId, "");
                return null;
            }
            Logger.error("Failed to screen scrape missing IMDB rating: URL: {} | Code: {}", r.uri().toString(), response.statusCode());
            return null;
        }
        
        var doc = Jsoup.parse(response.body());
        var ratingValue = doc.select("span[itemprop = ratingValue]");
        
        if(ratingValue.size() == 0) {
            var hasNoRating = doc.select("span.no-rating");
            if(hasNoRating.size() == 1) {
                if(!ImdbDockerImplementation.checkCapability(Capabilities.IGNORE_SCRAPER_NO_RESULT_LOG)) {
                    Logger.info("IMDB item with id {} has not been rated by anyone yet (on the IMDB website). Ignoring for {} day(s).", imdbId, SCRAPE_EVERY_N_DAYS_IGNORE);
                }
            } else {
                if(!ImdbDockerImplementation.checkCapability(Capabilities.IGNORE_SCRAPER_NO_RESULT_LOG)) {
                    Logger.info("IMDB item with id {} appears to not be allowed to be rated by anyone (on the IMDB website). Ignoring for {} day(s).", imdbId, SCRAPE_EVERY_N_DAYS_IGNORE);
                }
            }
            return null;
        }
        
        if(ratingValue.size() > 1) {
            throw new RuntimeException(String.format("Something went wrong with screen scraping the IMDB page for id %s (MORE_THAN_ONE_RESULT). Please contact developer by creating a GitHub issue.", imdbId));
        }
        
        String result = ratingValue.get(0).text();
        
        try {
            return result;
        } catch(Exception e) {
            throw new RuntimeException(String.format("Something went wrong with screen scraping the IMDB page for id %s (INVALID_VALUE) -> '%s'. Please contact developer by creating a GitHub issue.", imdbId, result));
        }
    }

    @Override
    public void close() {
        idScrapeExpire.dump();
        idCachedValue.dump();
    }
}