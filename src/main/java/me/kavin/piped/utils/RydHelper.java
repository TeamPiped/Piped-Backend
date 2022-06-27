package me.kavin.piped.utils;


import me.kavin.piped.consts.Constants;

import java.io.IOException;

public class RydHelper {
    public static double getDislikeRating(String videoId) throws IOException {

        if (Constants.DISABLE_RYD)
            return -1;

        return Constants.mapper.readTree(RequestUtils.sendGet(Constants.RYD_PROXY_URL + "/votes/" + videoId))
                .get("rating")
                .asDouble(-1);
    }
}
