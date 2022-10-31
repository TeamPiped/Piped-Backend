package me.kavin.piped.utils;


import me.kavin.piped.consts.Constants;

import java.io.IOException;

import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.RequestUtils.sendGet;

public class RydHelper {
    public static double getDislikeRating(String videoId) throws IOException {

        if (Constants.DISABLE_RYD)
            return -1;

        var value = mapper.readTree(sendGet(Constants.RYD_PROXY_URL + "/votes/" + videoId))
                .get("rating");

        return value == null ? -1 : value.asDouble(-1);
    }
}
