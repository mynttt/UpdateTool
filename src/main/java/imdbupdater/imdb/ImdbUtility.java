package imdbupdater.imdb;

import java.util.regex.Pattern;

public class ImdbUtility {
    private static final Pattern IMDB = Pattern.compile("tt[0-9]*");

    public static String extractImdbId(String guid) {
        var matcher = IMDB.matcher(guid);
        if(matcher.find())
            return matcher.group(0);
        return null;
    }
}
