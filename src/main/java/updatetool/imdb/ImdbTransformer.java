package updatetool.imdb;

import static updatetool.Globals.NEW_IMDB;
import static updatetool.Globals.OLD_IMDB;
import static updatetool.Globals.STRIP;
import static updatetool.Globals.isNewAgent;
import static updatetool.common.Utility.areEqualDouble;
import static updatetool.common.Utility.doubleToOneDecimalString;
import java.util.Map;
import org.tinylog.Logger;
import updatetool.api.ExportedRating;
import updatetool.common.ExtraData;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class ImdbTransformer {
    
    public static boolean needsUpdate(Map.Entry<ImdbMetadataResult, ExportedRating> check) {
        var meta = check.getKey();
        var imdb = check.getValue();
        double d = 0;
        
        try {
            d = Double.parseDouble(imdb.exportRating());
        } catch(NumberFormatException | NullPointerException e) {
            if(!ImdbRatingDatasetFactory.SCRAPE_FAILED.equals(imdb.exportRating())) {
                Logger.warn("Ignoring: '" + meta.title + "' with IMDB ID: " + meta.imdbId + " supplies no valid rating := '{}'", imdb.exportRating());
            }
            return false;
        }
        
        boolean isNewAgent = isNewAgent(meta);
        Double actualRating = isNewAgent ? meta.audienceRating : meta.rating;
        
        ExtraData e = ExtraData.of(meta.extraData);
        
        return actualRating == null
                || !areEqualDouble(actualRating, d, 3)
                || !e.contains(isNewAgent ? NEW_IMDB : OLD_IMDB)
                || e.containsAny(STRIP);
    }

    public static void updateMetadata(Map.Entry<ImdbMetadataResult, ExportedRating> target) {
        var meta = target.getKey();
        var imdb = target.getValue();
        double d = Double.parseDouble(imdb.exportRating());
        
        boolean isNewAgent = isNewAgent(meta);
        Double actualRating = isNewAgent ? meta.audienceRating : meta.rating;
        
        if(actualRating == null || !areEqualDouble(actualRating, d, 3)) {
            Logger.info("Adjust rating: " + doubleToOneDecimalString(actualRating) + " -> " + d + " for " + meta.title);
            
            if(isNewAgent) {
                meta.audienceRating = d;
            } else {
                meta.rating = d;
            }
        }
        
        ExtraData extra = ExtraData.of(meta.extraData);
        
        if(extra.containsAny(STRIP)) {
            Logger.info("(Remove) Stripping useless badge data (RT, TMDB, TVDB) for: {}", meta.title);
            STRIP.forEach(p -> extra.deleteKey(p.getKey()));
        }
        
        var targetBadge = isNewAgent ? NEW_IMDB : OLD_IMDB;
        if(!extra.contains(targetBadge)) {
            Logger.info("(Set) Set IMDB Badge for: {}", meta.title);
            extra.prepend(targetBadge.getKey(), targetBadge.getValue());
        }
        
        meta.extraData = extra.toURI();
    }

    // Cleaning required because of interesting data from TVDB
    public static String clean(String imdbId) {
        if(imdbId == null)
            return null;
        String numbers = ImdbUtility.extractId(ImdbUtility.NUMERIC, imdbId);
        return "tt"+numbers;
    }
}
