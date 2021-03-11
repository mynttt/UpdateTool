package updatetool;

import java.util.List;
import updatetool.common.Pair;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public final class Globals {

    private Globals() {}
    
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
