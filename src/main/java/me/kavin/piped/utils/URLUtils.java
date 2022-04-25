package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class URLUtils {

    public static String silentEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // ignored
        }
        return s;
    }

    public static String silentDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // ignored
        }
        return s;
    }

    public static String substringYouTube(String s) {
        return StringUtils.isEmpty(s) ? null : StringUtils.substringAfter(s, "youtube.com");
    }

    public static String rewriteURL(final String old) {
        return rewriteURL(old, Constants.IMAGE_PROXY_PART);
    }

    public static String rewriteVideoURL(final String old) {
        return rewriteURL(old, Constants.PROXY_PART);
    }

    public static String rewriteURL(final String old, final String proxy) {

        if (StringUtils.isEmpty(old)) return null;

        URL url = null;
        try {
            url = new URL(old);
        } catch (MalformedURLException e) {
            ExceptionHandler.handle(e);
        }
        assert url != null;

        final String host = url.getHost();

        String query = url.getQuery();

        boolean hasQuery = query != null;

        String path = url.getPath();

        if (path.contains("=")) {
            path = StringUtils.substringBefore(path, "=") + "=" + StringUtils.substringAfter(path, "=").replace("-rj", "-rw");
        }

        return proxy + path + (hasQuery ? "?" + query + "&host=" : "?host=") + silentEncode(host);

    }
}
