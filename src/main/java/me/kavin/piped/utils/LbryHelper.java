package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

import static me.kavin.piped.consts.Constants.h2client;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.RequestUtils.sendGet;
import static me.kavin.piped.utils.URLUtils.silentEncode;

public class LbryHelper {

    public static String getLBRYId(String videoId) throws IOException {

        if (Constants.DISABLE_LBRY)
            return null;

        return mapper.readTree(sendGet("https://api.lbry.com/yt/resolve?video_ids=" + silentEncode(videoId)))
                .at("/data/videos")
                .path(videoId)
                .asText(null);
    }

    public static String getLBRYStreamURL(String lbryId)
            throws IOException {

        if (StringUtils.isEmpty(lbryId))
            return null;

        var request = new Request.Builder()
                .url("https://api.lbry.tv/api/v1/proxy?m=get")
                .post(RequestBody.create(mapper.writeValueAsBytes(
                        mapper.createObjectNode()
                                .put("id", System.currentTimeMillis())
                                .put("id", System.currentTimeMillis())
                                .put("jsonrpc", "2.0")
                                .put("method", "get")
                                .set("params",
                                        mapper.createObjectNode()
                                                .put("uri", "lbry://" + lbryId)
                                                .put("save_file", true)
                                )
                ), MediaType.get("application/json")))
                .build();

        try (var resp = h2client.newCall(request).execute()) {
            if (resp.isSuccessful()) {
                return mapper.readTree(resp.body().byteStream())
                        .at("/result/streaming_url")
                        .asText(null);
            }
        }

        return null;
    }
}
