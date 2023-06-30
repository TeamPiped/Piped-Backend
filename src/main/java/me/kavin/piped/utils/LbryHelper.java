package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import org.apache.commons.lang3.StringUtils;
import rocks.kavin.reqwest4j.ReqwestUtils;
import rocks.kavin.reqwest4j.Response;

import java.net.URI;
import java.util.Map;

import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.URLUtils.silentEncode;

public class LbryHelper {

    public static String getLBRYId(String videoId) throws Exception {

        if (Constants.DISABLE_LBRY)
            return null;

        return RequestUtils.sendGetJson("https://api.lbry.com/yt/resolve?video_ids=" + silentEncode(videoId))
                .thenApplyAsync(json -> json.at("/data/videos")
                        .path(videoId)
                        .asText(null)
                ).get();
    }

    public static String getLBRYStreamURL(String lbryId)
            throws Exception {

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
                ), Map.of("Content-Type", "application/json")).get();
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

        // LBRY provides non UTF-8 characters in the URL, which causes issues
        streamUrl = new URI(streamUrl).toASCIIString();

        final String lastLocation = ReqwestUtils.fetch(streamUrl, "HEAD", null, Map.of(
                        "Origin", "https://odysee.com",
                        "Referer", "https://odysee.com/"
                ))
                .thenApply(Response::finalUrl)
                .get();

        return streamUrl.equals(lastLocation) ? null : lastLocation;
    }
}
