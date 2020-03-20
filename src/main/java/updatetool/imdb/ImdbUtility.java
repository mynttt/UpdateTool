package updatetool.imdb;

import java.util.regex.Pattern;

public class ImdbUtility {
    public static final Pattern IMDB = Pattern.compile("tt[0-9]*");
    public static final Pattern NUMERIC = Pattern.compile("[0-9]+");
    public static final Pattern TVDB_TMDB_SERIES_MATCHING = Pattern.compile("[0-9]+(\\/[0-9]+)*");
    public static final Pattern TVDB_TMDB_EPISODE = Pattern.compile("[0-9]+\\/[0-9]+\\/[0-9]+");
    public static final Pattern TVDB_TMDB_SEASON = Pattern.compile("[0-9]+\\/[0-9]+");
    public static final Pattern TVDB_TMDB_SERIES = Pattern.compile("[0-9]+");

    public static String extractId(Pattern pattern, String guid) {
        var matcher = pattern.matcher(guid);
        if(matcher.find())
            return matcher.group(0);
        return null;
    }

}
