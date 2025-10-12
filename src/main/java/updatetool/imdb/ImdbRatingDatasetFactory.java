package updatetool.imdb;

import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.tinylog.Logger;
import updatetool.Main;
import updatetool.api.ExportedRating;
import updatetool.common.Capabilities;
import updatetool.common.Utility;
import updatetool.exceptions.ImdbDatasetAcquireException;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public final class ImdbRatingDatasetFactory {
    public static final String SCRAPE_FAILED = "SCRAPE_FAILED";
    public static final String SCRAPE_DISABLED = "SCRAPE_DISABLED";
    
    private static URL urlExceptionHack() {
        
        try {
            return URI.create("https://datasets.imdbws.com/title.ratings.tsv.gz").toURL();
        } catch (Exception e) {
            throw Utility.rethrow(e);
        }
    }
    
    private static final URL DATASET_URL = urlExceptionHack();
    private static final String LAST_UPDATE = "ratingSetLastUpdate";
    private static final String RATING_SET_TEMP = "__tmp_rating.gz";
    private static final String RATING_SET = "rating_set.tsv";
    private static long UPDATE_DATA_INTERVAL = TimeUnit.DAYS.toMillis(1);

    private ImdbRatingDatasetFactory() {}
    
    public static class ScreenScrapedRating implements ExportedRating {
        private String rating, imdbId, title;
        private ImdbScraper scraper;
        
        public ScreenScrapedRating(String rating, String imdbId, String title, ImdbScraper scraper) {
            this.rating = rating;
            this.imdbId = imdbId;
            this.scraper = scraper;
            this.title = title;
        }

        @Override
        public String exportRating() {
            return rating;
        }

        @Override
        public void ensureAvailability() {
            if(rating == null) {
                try {
                    if(ImdbDockerImplementation.checkCapability(Capabilities.DISABLE_SCREEN_SCRAPE)) {
                        this.rating = SCRAPE_DISABLED;
                    } else {
                        var scraped = scraper.scrapeFallback(imdbId, title);
                        String scrapedRating = scraped == null ? SCRAPE_FAILED  : scraped;
                        this.rating = scrapedRating;
                    }
                } catch (Exception e) {
                    Logger.error(e.getClass().getSimpleName() + " exception encountered @ Screen Scraping [imdb={}]", imdbId);
                    Logger.error("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
                    Logger.error("========================================");
                    Logger.error(e);
                    Logger.error("========================================");
                } finally {
                    this.title = null;
                }
            }
        }
        
    }

    public static void requestSet() throws ImdbDatasetAcquireException {
        try {
            long lastUpdate = lastUpdated();
            if(System.currentTimeMillis() - UPDATE_DATA_INTERVAL >= lastUpdate) {
                Logger.info("IMDB Dataset has the timestamp: {} and violates the update every {} ms constraint. Refreshing dataset...", lastUpdate, UPDATE_DATA_INTERVAL);
                try {
                    fetchData();
                } catch(Exception e) {
                    if(!Files.exists(Main.PWD.resolve(RATING_SET)))
                       throw e;
                    Logger.error(e);
                    Logger.error("Failed to fetch IMDB rating data set due to {}. Fallback on existing dataset with timestamp {}.", e.getClass().getSimpleName(), lastUpdate);
                }
            }
            if(!Files.exists(Main.PWD.resolve(RATING_SET))) {
                Logger.info("IMDB Dataset not found (./{}). Refreshing dataset...", RATING_SET);
                fetchData();
            }
        } catch(Exception e) {
            throw new ImdbDatasetAcquireException("Failed to acquire imdb dataset!", e);
        }
    }
    
    private static long lastUpdated() {
        var p = Main.PWD.resolve(LAST_UPDATE);
        try {
            return Long.parseLong(Files.readString(p));
        } catch (NumberFormatException | IOException e) {
            return 0;
        }
    }
    
    private static void fetchData() {
        try {
            downloadData();
            extractData();
            Files.writeString(Main.PWD.resolve(LAST_UPDATE), Long.toString(System.currentTimeMillis()));
        } catch (IOException e) {
            try {
                Files.deleteIfExists(Main.PWD.resolve(RATING_SET_TEMP));
                Files.deleteIfExists(Main.PWD.resolve(RATING_SET));
            } catch (IOException e1) {}
            throw Utility.rethrow(e);
        }
    }
    
    private static void downloadData() throws IOException {
        Logger.info("Downloading IMDB rating set from: {}", DATASET_URL.toString());
        var in = DATASET_URL.openStream();
        Files.copy(in, Main.PWD.resolve(RATING_SET_TEMP), StandardCopyOption.REPLACE_EXISTING);
        Logger.info("Download succeeded @ ./{}", RATING_SET_TEMP);
    }
    
    private static void extractData() throws IOException {
        Logger.info("Extracting dataset...");
        var gzip = new GZIPInputStream(Files.newInputStream(Main.PWD.resolve(RATING_SET_TEMP)));
        Files.copy(gzip, Main.PWD.resolve(RATING_SET), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(Main.PWD.resolve(RATING_SET_TEMP));
        Logger.info("Extraction completed.");
    }
    
    public static HashMap<ImdbMetadataResult, ExportedRating> loadFromDataset(List<ImdbMetadataResult> items, ImdbScraper scraper) {
        var data = new HashMap<ImdbMetadataResult, ExportedRating>();
        
        var lookup = new HashMap<String, ImdbMetadataResult>();
        items.forEach(i -> lookup.put(i.imdbId, i));
        
        // Stage 1: Process ITEMS that are for sure in the dataset
        Logger.info("Reading data of IMDB Dataset via Buffer...");
        try(var reader = Files.newBufferedReader(Main.PWD.resolve(RATING_SET))) {
            reader.readLine(); //Skip header
            String s;
            while((s = reader.readLine()) != null) {
                var split = s.split("\\s+");
                var imdbId = split[0];
                var rating = split[1];
                
                var candidate = lookup.remove(imdbId);
                
                if(candidate != null) {
                    data.put(candidate, new ScreenScrapedRating(rating, imdbId, candidate.title, scraper));
                }
            }
        } catch(IOException e) {
            throw Utility.rethrow(e);
        }
        
        // Stage 2: Prepare leftovers to be screenscraped if enabled
        lookup.values().forEach(i -> data.put(i, new ScreenScrapedRating(null, i.imdbId, i.title, scraper)));

        return data;
    }
}
