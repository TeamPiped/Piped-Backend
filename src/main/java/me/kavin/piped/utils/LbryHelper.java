package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import org.apache.commons.lang3.StringUtils;
import rocks.kavin.reqwest4j.ReqwestUtils;

import java.io.IOException;
import java.util.Map;

import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.URLUtils.silentEncode;

public class LbryHelper {

    public static String getLBRYId(String videoId) throws IOException {

        if (Constants.DISABLE_LBRY)
            return null;

        return RequestUtils.sendGetJson("https://api.lbry.com/yt/resolve?video_ids=" + silentEncode(videoId))
                .at("/data/videos")
                .path(videoId)
                .asText(null);
    }

    public static String getLBRYStreamURL(String lbryId)
            throws IOException {

        if (StringUtils.isEmpty(lbryId))
            return null;

        var resp = ReqwestUtils.fetch("https://api.na-backend.odysee.com/api/v1/proxy?m=get", "POST",
                mapper.writeValueAsBytes(
                        mapper.createObjectNode()
                                .put("id", System.currentTimeMillis())
                                .put("jsonrpc", "2.0")
                                .put("method", "get")
                                .set("params",
                                        mapper.createObjectNode()
                                                .put("uri", "lbry://" + lbryId)
                                                .put("save_file", true)
                                )
                ), Map.of("Content-Type", "application/json"));
        if (resp.status() / 100 == 2) {
            return mapper.readTree(resp.body())
                    .at("/result/streaming_url")
                    .asText(null);
        }

        return null;
    }


    public static String getLBRYHlsUrl(String streamUrl) throws Exception {

        if (StringUtils.isEmpty(streamUrl))
            return null;

        var resp = ReqwestUtils.fetch(streamUrl, "HEAD", null, Map.of(
                "Origin", "https://odysee.com",
                "Referer", "https://odysee.com/"
        ));

        final String lastLocation = resp.finalUrl();

        return streamUrl.equals(lastLocation) ? null : lastLocation;
    }
}
