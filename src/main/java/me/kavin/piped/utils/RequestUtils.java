package me.kavin.piped.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import me.kavin.piped.consts.Constants;

public class RequestUtils {

    public static String sendGet(String url) throws IOException, InterruptedException, URISyntaxException {
        return sendGet(url, Constants.USER_AGENT);
    }

    public static String sendGet(String url, String ua) throws IOException, InterruptedException, URISyntaxException {

        HttpRequest request = HttpRequest.newBuilder(new URI(url)).GET().setHeader("User-Agent", ua).build();

        return Constants.h2client.send(request, BodyHandlers.ofString()).body();
    }
}
