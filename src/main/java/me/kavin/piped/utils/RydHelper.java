package me.kavin.piped.utils;


import me.kavin.piped.consts.Constants;

import static me.kavin.piped.utils.RequestUtils.sendGetJson;

public class RydHelper {
    public static double getDislikeRating(String videoId) throws Exception {

        if (Constants.DISABLE_RYD)
            return -1;

        return sendGetJson(Constants.RYD_PROXY_URL + "/votes/" + videoId)
                .thenApply(tree -> tree.path("rating").asDouble(-1))
                .get();

    }
}
