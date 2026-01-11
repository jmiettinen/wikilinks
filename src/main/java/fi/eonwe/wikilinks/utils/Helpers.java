package fi.eonwe.wikilinks.utils;

/**
 */
public class Helpers {

    public static String quote(String str) {
        return "\"" + str + "\"";
    }

    public static int saturatedCast(long value) {
        if (value > 2147483647L) {
            return Integer.MAX_VALUE;
        } else {
            return value < -2147483648L ? Integer.MIN_VALUE : (int)value;
        }
    }


}
