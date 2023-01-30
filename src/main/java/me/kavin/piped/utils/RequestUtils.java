package me.kavin.piped.utils;

import com.fasterxml.jackson.databind.JsonNode;
import me.kavin.piped.consts.Constants;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import rocks.kavin.reqwest4j.ReqwestUtils;
import rocks.kavin.reqwest4j.Response;

import java.io.IOException;
import java.util.Map;

import static me.kavin.piped.consts.Constants.mapper;

public class RequestUtils {

    public static Response sendGetRaw(String url) throws IOException {
        return ReqwestUtils.fetch(url, "GET", null, Map.of());
    }

    public static String sendGet(String url) throws IOException {
        return new String(
                ReqwestUtils.fetch(url, "GET", null, Map.of())
                        .body()
        );
    }

    public static String sendGet(String url, String ua) throws IOException {
        return new String(
                ReqwestUtils.fetch(url, "GET", null, Map.of("User-Agent", ua))
                        .body()
        );
    }

    public static JsonNode getJsonNode(OkHttpClient client, Request request) throws IOException {
        try (var resp = client.newCall(request).execute()) {
            try {
                return mapper.readTree(resp.body().byteStream());
            } catch (Exception e) {
                if (!resp.isSuccessful())
                    ExceptionHandler.handle(e);
                throw new RuntimeException("Failed to parse JSON", e);
            }
        }
    }

    public static JsonNode sendGetJson(String url, String ua) throws IOException {
        return getJsonNode(Constants.h2client, new Request.Builder().header("User-Agent", ua).url(url).build());
    }

    public static JsonNode sendGetJson(String url) throws IOException {

        return mapper.readTree(ReqwestUtils.fetch(url, "GET", null, Map.of()).body());

    }
}
