package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import okhttp3.Request;

import java.io.IOException;

public class RequestUtils {

    public static String sendGet(String url) throws IOException {
        return sendGet(url, Constants.USER_AGENT);
    }

    public static String sendGet(String url, String ua) throws IOException {

        var request = new Request.Builder().header("User-Agent", ua).url(url).build();
        var response = Constants.h2client.newCall(request).execute();
        var responseString = response.body().string();
        response.close();

        return responseString;
    }
}
