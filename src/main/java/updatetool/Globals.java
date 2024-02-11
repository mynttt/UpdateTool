package updatetool;

import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import updatetool.common.Pair;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public final class Globals {

    private Globals() {}
    
    // This is set in ImdbDatabaseSupport to understand if we use the new or old format for null valued ExtraData that is then initiated by the tool for the first time
    @SuppressFBWarnings("MS_CANNOT_BE_FINAL")
    public static boolean IS_NEW_EXTRA_DATA = false;
    
    /*
     * Badges
     */
    
    public static final Pair<String, String> 
        NEW_TMDB = Pair.of("at:audienceRatingImage", "themoviedb://image.rating"),
        NEW_IMDB = Pair.of("at:audienceRatingImage", "imdb://image.rating"),
        NEW_TVDB = Pair.of("at:audienceRatingImage", "thetvdb://image.rating"),
        ROTTEN_A = Pair.of("at:audienceRatingImage", "rottentomatoes://image.rating.upright"),
        ROTTEN_R = Pair.of("at:ratingImage", "rottentomatoes://image.rating.ripe"),
        OLD_IMDB = Pair.of("at:ratingImage", "imdb://image.rating");

    public static final List<Pair<String, String>> STRIP = List.of(NEW_TMDB, ROTTEN_A, ROTTEN_R, NEW_TVDB);
    
    /*
     * Agents
     */
    
    private static final List<String> NEW_AGENTS = List.of(
            "plex://movie/", 
            "plex://season/", 
            "plex://episode/", 
            "plex://show/"
        );
    
    public static boolean isNewAgent(ImdbMetadataResult meta) {
        for(String newagent : NEW_AGENTS) {
            if(meta.guid.startsWith(newagent))
                return true;
        }
        return false;
    }
}
