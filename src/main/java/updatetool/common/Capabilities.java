package updatetool.common;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public enum Capabilities {
    TMDB, TVDB, NO_TV, NO_MOVIE, VERBOSE_XML_ERROR_LOG;
    
    private static final List<Capabilities> USER_FLAGS = List.of(NO_MOVIE, NO_TV);

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