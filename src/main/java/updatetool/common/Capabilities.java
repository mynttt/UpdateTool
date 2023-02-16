package updatetool.common;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public enum Capabilities {
    TMDB, 
    TVDB, 
    NO_TV, 
    NO_MOVIE, 
    DONT_THROW_ON_ENCODING_ERROR,
    IGNORE_SCRAPER_NO_RESULT_LOG,
    IGNORE_NO_MATCHING_RESOLVER_LOG,
    DISABLE_SCREEN_SCRAPE,
    PRINT_SQLITE_BINARY_EXECUTE_STATEMENTS;
    
    private static final List<Capabilities> USER_FLAGS = List.of(NO_MOVIE, NO_TV, DONT_THROW_ON_ENCODING_ERROR, IGNORE_NO_MATCHING_RESOLVER_LOG, IGNORE_SCRAPER_NO_RESULT_LOG, DISABLE_SCREEN_SCRAPE, PRINT_SQLITE_BINARY_EXECUTE_STATEMENTS);

    public static List<Capabilities> getUserFlags() {
        return USER_FLAGS;
    }
    
    @SuppressWarnings("serial")
    public static String formatMovie(EnumSet<Capabilities> capabilities) {
        List<String> agents = new ArrayList<>() {{ 
            add("'com.plexapp.agents.imdb'");
            add("'tv.plex.agents.movie'");
        }};
        
        if(capabilities.contains(Capabilities.TMDB))
            agents.add("'com.plexapp.agents.themoviedb'");
        
        return String.join(", ", agents);
    }
    
    public static String formatSeries(EnumSet<Capabilities> capabilities) {
        List<String> agents = new ArrayList<>();
        
        agents.add("'tv.plex.agents.series'");
        if(capabilities.contains(Capabilities.TMDB))
            agents.add("'com.plexapp.agents.themoviedb'");
        if(capabilities.contains(Capabilities.TVDB))
            agents.add("'com.plexapp.agents.thetvdb'");
        
        return String.join(", ", agents);
    }
}