package updatetool.imdb;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.tinylog.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import updatetool.Main;
import updatetool.api.ExportedRating;
import updatetool.common.Utility;
import updatetool.exceptions.ImdbDatasetAcquireException;

public final class ImdbRatingDatasetFactory {
    
    private static URL urlExceptionHack() {
        try {
            return new URL("https://datasets.imdbws.com/title.ratings.tsv.gz");
        } catch (MalformedURLException e) {
            throw Utility.rethrow(e);
        }
    }
    
    private static final URL DATASET_URL = urlExceptionHack();
    private static final String LAST_UPDATE = "ratingSetLastUpdate";
    private static final String RATING_SET_TEMP = "__tmp_rating.gz";
    private static final String RATING_SET = "rating_set.tsv";
    private static long UPDATE_DATA_INTERVAL = TimeUnit.DAYS.toMillis(1);

    private ImdbRatingDatasetFactory() {}
    
    public static class ImdbRatingDataset {
        private HashMap<String, String> data = new HashMap<>();

        public ExportedRating getRatingFor(String imdbId) {
            String rating = data.get(imdbId);
            return () -> rating;
        }
    }

    public static ImdbRatingDataset requestSet() throws ImdbDatasetAcquireException {
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
            var data = new ImdbRatingDataset();
            readData(data);
            return data;
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
    
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    private static void readData(ImdbRatingDataset target) {
        Logger.info("Reading data...");
        try(var reader = Files.newBufferedReader(Main.PWD.resolve(RATING_SET))) {
            reader.readLine(); //Skip header
            String s;
            while((s = reader.readLine()) != null) {
                var split = s.split("\\s+");
                target.data.put(split[0], split[1]);
            }
        } catch(IOException e) {
            throw Utility.rethrow(e);
        }
        Logger.info("{} lines read.", target.data.size());
    }
    
}
