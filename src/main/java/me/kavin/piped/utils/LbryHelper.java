package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

import java.io.IOException;

public class LbryHelper {

    public static String getLBRYId(String videoId) throws IOException, InterruptedException {
        return new JSONObject(
                RequestUtils.sendGet("https://api.lbry.com/yt/resolve?video_ids=" + URLUtils.silentEncode(videoId))
        ).getJSONObject("data").getJSONObject("videos").optString(videoId, null);
    }

    public static String getLBRYStreamURL(String lbryId)
            throws IOException {
        var request = new Request.Builder()
                .url("https://api.lbry.tv/api/v1/proxy?m=get")
                .post(RequestBody.create(String.valueOf(
                        new JSONObject().put("id", System.currentTimeMillis())
                                .put("jsonrpc", "2.0")
                                .put("method", "get")
                                .put("params",
                                        new JSONObject()
                                                .put("uri", "lbry://" + lbryId)
                                                .put("save_file", true)))
                        , MediaType.get("application/json")))
                .build();

        return new JSONObject(
                Constants.h2client.newCall(request).execute().body().string()
        ).getJSONObject("result").getString("streaming_url");

    }
}
