package me.kavin.piped.utils;


import me.kavin.piped.consts.Constants;

import java.io.IOException;

import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.RequestUtils.sendGetRaw;

public class RydHelper {
    public static double getDislikeRating(String videoId) throws IOException {

        if (Constants.DISABLE_RYD)
            return -1;

        try (var resp = sendGetRaw(Constants.RYD_PROXY_URL + "/votes/" + videoId)) {

            if (!resp.isSuccessful())
                return -1;

            return mapper.readTree(resp.body().byteStream())
                    .path("rating")
                    .asDouble(-1);

        }
    }
}
