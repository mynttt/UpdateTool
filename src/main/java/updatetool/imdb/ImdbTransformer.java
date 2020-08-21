package updatetool.imdb;

import static updatetool.common.Utility.areEqualDouble;
import static updatetool.common.Utility.doubleToOneDecimalString;
import java.util.List;
import java.util.Map;
import org.tinylog.Logger;
import updatetool.api.ExportedRating;
import updatetool.common.ExtraData;
import updatetool.common.Pair;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class ImdbTransformer {
    
    private static final Pair<String, String> 
        NEW_TMDB = Pair.of("at:audienceRatingImage", "themoviedb://image.rating"),
        NEW_IMDB = Pair.of("at:audienceRatingImage", "imdb://image.rating"),
        ROTTEN_A = Pair.of("at:audienceRatingImage", "rottentomatoes://image.rating.upright"),
        ROTTEN_R = Pair.of("at:ratingImage", "rottentomatoes://image.rating.ripe"),
        OLD_IMDB = Pair.of("at:ratingImage", "imdb://image.rating");
    
    private static final List<Pair<String, String>> STRIP = List.of(NEW_TMDB, ROTTEN_A, ROTTEN_R);
    
    public static boolean needsUpdate(Map.Entry<ImdbMetadataResult, ExportedRating> check) {
        var meta = check.getKey();
        var imdb = check.getValue();
        double d = 0;
        
        try {
            d = Double.parseDouble(imdb.exportRating());
        } catch(NumberFormatException | NullPointerException e) {
            Logger.warn("Ignoring: '" + meta.title + "' with IMDB ID: " + meta.imdbId + " supplies no valid rating := '{}'", imdb.exportRating());
            return false;
        }
        
        boolean isNewMovieAgent = meta.guid.startsWith("plex://movie/");
        Double actualRating = isNewMovieAgent ? meta.audienceRating : meta.rating;
        
        ExtraData e = ExtraData.of(meta.extraData);
        
        return actualRating == null
                || !areEqualDouble(actualRating, d, 3)
                || !e.contains(isNewMovieAgent ? NEW_IMDB : OLD_IMDB)
                || e.containsAny(STRIP);
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
        
        ExtraData extra = ExtraData.of(meta.extraData);
        
        if(extra.containsAny(STRIP)) {
            Logger.info("(Remove) Stripping useless badge data (RT, TMDB) for: {}", meta.title);
            STRIP.forEach(p -> extra.deleteKey(p.getKey()));
        }
        
        var targetBadge = isNewMovieAgent ? NEW_IMDB : OLD_IMDB;
        if(!extra.contains(targetBadge)) {
            Logger.info("(Set) Set IMDB Badge for: {}", meta.title);
            extra.prepend(targetBadge.getKey(), targetBadge.getValue());
        }
        
        meta.extraData = extra.toURI();
    }
}
