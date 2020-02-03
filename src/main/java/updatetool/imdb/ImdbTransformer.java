package updatetool.imdb;

import static updatetool.common.Utility.areEqualDouble;
import static updatetool.common.Utility.doubleToOneDecimalString;
import java.util.Map;
import org.tinylog.Logger;
import updatetool.api.ExportedRating;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class ImdbTransformer {
    private static final String IMDB_FLAG_EMPTY = "at%3AratingImage=imdb%3A%2F%2Fimage%2Erating";
    private static final String IMDB_FLAG = "at%3AratingImage=imdb%3A%2F%2Fimage%2Erating&";

    public static boolean needsNoUpdate(Map.Entry<ImdbMetadataResult, ExportedRating> check) {
        var meta = check.getKey();
        var imdb = check.getValue();
        double d = 0;
        try {
            d = Double.parseDouble(imdb.exportRating());
        } catch(NumberFormatException | NullPointerException e) {
            Logger.warn("Ignoring: " + meta.title + " with IMDB ID: " + meta.imdbId + " supplies no valid rating (class=" + imdb.getClass().getCanonicalName() + "): " + imdb.exportRating());
            return true;
        }
        return meta.rating != null
                && areEqualDouble(meta.rating, d, 3)
                && meta.extraData.contains("ratingImage=imdb");
    }

    public static void updateMetadata(Map.Entry<ImdbMetadataResult, ExportedRating> target) {
        var meta = target.getKey();
        var imdb = target.getValue();
        double d = Double.parseDouble(imdb.exportRating());
        if(meta.rating == null || !areEqualDouble(meta.rating, d, 3)) {
            Logger.info("Adjust rating: " + doubleToOneDecimalString(meta.rating) + " -> " + d + " for " + meta.title);
            meta.rating = d;
        }
        if(!meta.extraData.contains("ratingImage=imdb")) {
            if(meta.extraData.trim().isEmpty()) {
                Logger.info("(Set) Set IMDB Badge for: " + meta.title);
                meta.extraData = IMDB_FLAG_EMPTY;
            } else {
                Logger.info("(Prepend) Set IMDB Badge for: " + meta.title);
                meta.extraData = IMDB_FLAG+meta.extraData;
            }
        }
    }
}
