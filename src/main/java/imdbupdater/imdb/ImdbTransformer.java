package imdbupdater.imdb;

import static common.Utility.areEqualDouble;
import static common.Utility.doubleToOneDecimalString;
import java.util.Map;
import org.tinylog.Logger;
import common.OmdbApi.OMDBResponse;
import imdbupdater.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class ImdbTransformer {
    private static final String IMDB_FLAG_EMPTY = "at%3AratingImage=imdb%3A%2F%2Fimage%2Erating";
    private static final String IMDB_FLAG = "at%3AratingImage=imdb%3A%2F%2Fimage%2Erating&";


    public static boolean needsNoUpdate(Map.Entry<ImdbMetadataResult, OMDBResponse> check) {
        var meta = check.getKey();
        var imdb = check.getValue();
        double d = 0;
        try {
            d = Double.parseDouble(imdb.imdbRating);
        } catch(NumberFormatException e) {
            Logger.warn("Ignoring: " + meta.title + " with IMDB ID: " + meta.imdbId + " supplies no valid rating from OMDB: " + imdb.imdbRating);
            return true;
        }
        return meta.rating != null
                && areEqualDouble(meta.rating, d, 3)
                && meta.extraData.contains("ratingImage=imdb");
    }

    public static void updateMetadata(Map.Entry<ImdbMetadataResult, OMDBResponse> target) {
        var meta = target.getKey();
        var imdb = target.getValue();
        double d = Double.parseDouble(imdb.imdbRating);
        if(meta.rating == null || !areEqualDouble(meta.rating, d, 3)) {
            System.out.println("Adjust rating: " + doubleToOneDecimalString(meta.rating) + " -> " + d + " for " + meta.title);
            meta.rating = d;
        }
        if(!meta.extraData.contains("ratingImage=imdb")) {
            if(meta.extraData.trim().isEmpty()) {
                System.out.println("Set IMDB Flag for: " + meta.title);
                meta.extraData = IMDB_FLAG_EMPTY;
            } else {
                System.out.println("Preprend IMDB Flag for: " + meta.title);
                meta.extraData = IMDB_FLAG+meta.extraData;
            }
        }
    }
}
