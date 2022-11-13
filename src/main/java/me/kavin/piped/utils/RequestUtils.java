package me.kavin.piped.utils;

import com.fasterxml.jackson.databind.JsonNode;
import me.kavin.piped.consts.Constants;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class RequestUtils {

    public static Response sendGetRaw(String url) throws IOException {
        return sendGetRaw(url, Constants.USER_AGENT);
    }

    public static Response sendGetRaw(String url, String ua) throws IOException {
        var request = new Request.Builder().header("User-Agent", ua).url(url).build();
        return Constants.h2client.newCall(request).execute();
    }

    public static String sendGet(String url) throws IOException {
        return sendGet(url, Constants.USER_AGENT);
    }

    public static String sendGet(String url, String ua) throws IOException {

        var response = sendGetRaw(url, ua);
        var responseString = response.body().string();
        response.close();

        return responseString;
    }

    public static JsonNode getJsonNode(OkHttpClient client, Request request) throws IOException {
        try (var resp = client.newCall(request).execute()) {
            try {
                return Constants.mapper.readTree(resp.body().byteStream());
            } catch (Exception e) {
                if (!resp.isSuccessful())
                    ExceptionHandler.handle(e);
                throw new RuntimeException("Failed to parse JSON", e);
            }
        }
    }
}
