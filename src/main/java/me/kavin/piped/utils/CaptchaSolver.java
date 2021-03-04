package me.kavin.piped.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.SolvedCaptcha;

public class CaptchaSolver {

    public static SolvedCaptcha solve(String url, String sitekey, String data_s)
            throws JsonParserException, IOException, InterruptedException {

        int taskId = createTask(url, sitekey, data_s);

        return waitForSolve(taskId);

    }

    private static int createTask(String url, String sitekey, String data_s)
            throws JsonParserException, IOException, InterruptedException {

        Builder builder = HttpRequest.newBuilder(URI.create(Constants.CAPTCHA_BASE_URL + "createTask"));
        JsonObject jObject = new JsonObject();
        jObject.put("clientKey", Constants.CAPTCHA_API_KEY);
        {
            JsonObject task = new JsonObject();
            task.put("type", "NoCaptchaTaskProxyless");
            task.put("websiteURL", url);
            task.put("websiteKey", sitekey);
            task.put("recaptchaDataSValue", data_s);
            jObject.put("task", task);
        }

        builder.method("POST", BodyPublishers.ofString(JsonWriter.string(jObject)));

        builder.header("Content-Type", "application/json");

        JsonObject taskObj = JsonParser.object()
                .from(Constants.h2client.send(builder.build(), BodyHandlers.ofInputStream()).body());

        return taskObj.getInt("taskId");
    }

    private static final SolvedCaptcha waitForSolve(int taskId)
            throws JsonParserException, IOException, InterruptedException {

        String body = JsonWriter.string(
                JsonObject.builder().value("clientKey", Constants.CAPTCHA_API_KEY).value("taskId", taskId).done());

        SolvedCaptcha solved = null;

        outer: while (true) {
            Builder builder = HttpRequest.newBuilder(URI.create(Constants.CAPTCHA_BASE_URL + "getTaskResult"));

            builder.method("POST", BodyPublishers.ofString(body));

            builder.header("Content-Type", "application/json");

            JsonObject captchaObj = JsonParser.object()
                    .from(Constants.h2client.send(builder.build(), BodyHandlers.ofInputStream()).body());

            if (captchaObj.getInt("errorId") != 0)
                break;

            if (captchaObj.has("solution")) {
                JsonObject solution = captchaObj.getObject("solution");
                String captchaResp = solution.getString("gRecaptchaResponse");
                JsonObject cookieObj = solution.getObject("cookies");
                Map<String, String> cookies = new Object2ObjectOpenHashMap<>();

                if (captchaResp != null) {

                    cookieObj.keySet().forEach(cookie -> {
                        cookies.put(cookie, cookieObj.getString(cookie));
                    });

                    solved = new SolvedCaptcha(cookies, captchaResp);
                    break outer;
                }
            }

            Thread.sleep(1000);
        }

        return solved;
    }
}
