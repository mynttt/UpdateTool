package updatetool.common;

import java.text.DecimalFormat;
import java.time.Duration;
import org.tinylog.Logger;

public class Utility {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.0");

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable target) throws T {
        throw (T) target;
    }

    public static boolean areEqualDouble(double a, double b, int precision) {
        return Math.abs(a - b) <= Math.pow(10, -precision);
    }
    
    public static String humanReadableFormat(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
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

    public static String doubleToOneDecimalString(Double d) {
        if(d == null)
            return "null";
        return DECIMAL_FORMAT.format(d);
    }

    public static int parseHourIntOrFallback(String hour, int fallback, String name) {
        boolean error = false;
        int i = -1;
        
        try {
            i = Integer.parseInt(hour);
            error = i <= 0;
        } catch(NumberFormatException e) {
            error = true;
        }
        
        if(error) {
            Logger.error("Invalid parameter for: {} (must be number and > 0)", name);
            return fallback;
        }
        
        return i;
    }

}
