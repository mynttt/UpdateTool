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
    
    private static final String N_RT_FLAG = "at%3AaudienceRatingImage=rottentomatoes%3A%2F%2Fimage%2Erating%2Eupright&at%3AratingImage=rottentomatoes%3A%2F%2Fimage%2Erating%2Eripe";
    private static final String N_IMDB_FLAG_EMPTY = "at%3AaudienceRatingImage=imdb%3A%2F%2Fimage%2Erating";
    private static final String N_IMDB_FLAG = "at%3AaudienceRatingImage=imdb%3A%2F%2Fimage%2Erating&";
    
    public static boolean needsNoUpdate(Map.Entry<ImdbMetadataResult, ExportedRating> check) {
        var meta = check.getKey();
        var imdb = check.getValue();
        double d = 0;
        
        try {
            d = Double.parseDouble(imdb.exportRating());
        } catch(NumberFormatException | NullPointerException e) {
            Logger.warn("Ignoring: '" + meta.title + "' with IMDB ID: " + meta.imdbId + " supplies no valid rating := '{}'", imdb.exportRating());
            return true;
        }
        
        boolean isNewMovieAgent = meta.guid.startsWith("plex://movie/");
        Double actualRating = isNewMovieAgent ? meta.audienceRating : meta.rating;
        
        return actualRating != null
                && areEqualDouble(actualRating, d, 3)
                && (isNewMovieAgent ? meta.extraData.contains("audienceRatingImage=imdb") : meta.extraData.contains("ratingImage=imdb"));
    }

    public static void updateMetadata(Map.Entry<ImdbMetadataResult, ExportedRating> target) {
        var meta = target.getKey();
        var imdb = target.getValue();
        double d = Double.parseDouble(imdb.exportRating());
        
        boolean isNewMovieAgent = meta.guid.startsWith("plex://movie/");
        Double actualRating = isNewMovieAgent ? meta.audienceRating : meta.rating;
        
        if(actualRating == null || !areEqualDouble(actualRating, d, 3)) {
            Logger.info("Adjust rating: " + doubleToOneDecimalString(actualRating) + " -> " + d + " for " + meta.title);
            
            if(isNewMovieAgent) {
                meta.audienceRating = d;
            } else {
                meta.rating = d;
            }
        }
        
        if(isNewMovieAgent && meta.extraData.contains(N_RT_FLAG)) {
            meta.extraData = meta.extraData.replace(N_RT_FLAG, N_IMDB_FLAG_EMPTY);
        }
        
        if(!meta.extraData.contains(isNewMovieAgent ? "audienceRatingImage=imdb" : "ratingImage=imdb")) {
            if(meta.extraData.trim().isEmpty()) {
                Logger.info("(Set) Set IMDB Badge for: " + meta.title);
                meta.extraData = isNewMovieAgent ? N_IMDB_FLAG_EMPTY : IMDB_FLAG_EMPTY;
            } else {
                Logger.info("(Prepend) Set IMDB Badge for: " + meta.title);
                meta.extraData = isNewMovieAgent ? (N_IMDB_FLAG+meta.extraData) : (IMDB_FLAG+meta.extraData);
            }
        }
    }
}
