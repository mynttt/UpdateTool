package ImdbUpdater;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.regex.Pattern;
import ImdbUpdater.OMDBApi.OMDBResponse;
import ImdbUpdater.PlexDatabaseSupport.MetadataResult;

public class Utility {
    private static final Pattern IMDB = Pattern.compile("tt[0-9]*");
    private static final String IMDB_FLAG_EMPTY = "at%3AratingImage=imdb%3A%2F%2Fimage%2Erating";
    private static final String IMDB_FLAG = "at%3AratingImage=imdb%3A%2F%2Fimage%2Erating&";
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable target) throws T {
        throw (T) target;
    }

    public static boolean areEqualDouble(double a, double b, int precision) {
        return Math.abs(a - b) <= Math.pow(10, -precision);
     }

    public static String seperator(String s) {
        return "======[ " + s + " ]======";
    }

    public static void printStatusBar(int current, int max, int width) {
        int symbols = current*width/max;
        int percentage = (int) (current/(1.0*max)*100);
        StringBuilder sb = new StringBuilder(80);
        sb.append('\r');
        sb.append("Progress: [");
        for(int i = 0; i<width; i++)
            sb.append((i<=symbols) ? '#' : '-');
        sb.append("] ");
        sb.append(percentage);
        sb.append(" %");
        System.out.print(sb.toString());
        if(max == current)
            System.out.println();
    }

    public static String extractImdbId(String guid) {
        var matcher = IMDB.matcher(guid);
        if(matcher.find())
            return matcher.group(0);
        return null;
    }

    public static boolean needsNoUpdate(Map.Entry<MetadataResult, OMDBResponse> check) {
        return check.getKey().rating != null
                && areEqualDouble(check.getKey().rating, Double.parseDouble(check.getValue().imdbRating), 3)
                && check.getKey().extraData.contains("ratingImage=imdb");
    }

    public static String doubleToOneDecimalString(Double d) {
        if(d == null)
            return "null";
        return DECIMAL_FORMAT.format(d);
    }

    public static void updateMetadata(Map.Entry<MetadataResult, OMDBResponse> target) {
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
