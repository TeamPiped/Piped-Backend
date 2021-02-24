package me.kavin.piped.utils;

import java.net.URLEncoder;

public class URLUtils {

    public static String silentEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            // ignored
        }
        return s;
    }
}
