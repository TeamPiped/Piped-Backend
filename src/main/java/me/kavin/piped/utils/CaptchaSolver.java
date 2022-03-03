package me.kavin.piped.utils;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.SolvedCaptcha;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.Map;

public class CaptchaSolver {

    public static SolvedCaptcha solve(String url, String sitekey, String data_s)
            throws JsonParserException, IOException, InterruptedException {

        int taskId = createTask(url, sitekey, data_s);

        return waitForSolve(taskId);

    }

    private static int createTask(String url, String sitekey, String data_s)
            throws JsonParserException, IOException {

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

        var builder = new Request.Builder().url(Constants.CAPTCHA_BASE_URL + "createTask")
                .post(RequestBody.create(JsonWriter.string(jObject), MediaType.get("application/json")));
        var resp = Constants.h2client.newCall(builder.build()).execute();

        JsonObject taskObj = JsonParser.object()
                .from(resp.body().byteStream());

        resp.close();

        return taskObj.getInt("taskId");
    }

    private static SolvedCaptcha waitForSolve(int taskId)
            throws JsonParserException, IOException, InterruptedException {

        String body = JsonWriter.string(
                JsonObject.builder().value("clientKey", Constants.CAPTCHA_API_KEY).value("taskId", taskId).done());

        SolvedCaptcha solved = null;

        while (true) {
            var builder = new Request.Builder()
                    .url(Constants.CAPTCHA_BASE_URL + "getTaskResult")
                    .post(RequestBody.create(body, MediaType.get("application/json")));

            builder.header("Content-Type", "application/json");
            var resp = Constants.h2client.newCall(builder.build()).execute();


            JsonObject captchaObj = JsonParser.object()
                    .from(resp.body().byteStream());

            resp.close();

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
                    break;
                }
            }

            Thread.sleep(1000);
        }

        return solved;
    }
}
