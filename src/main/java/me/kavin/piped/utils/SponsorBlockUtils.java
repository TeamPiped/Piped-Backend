package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import me.kavin.piped.utils.resp.SimpleErrorMessage;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

import static me.kavin.piped.consts.Constants.mapper;

public class SponsorBlockUtils {

    public static String getSponsors(String id, String categories)
            throws IOException {

        if (StringUtils.isEmpty(categories))
            return mapper.writeValueAsString(new InvalidRequestResponse());

        String hash = DigestUtils.sha256Hex(id);

        for (String url : Constants.SPONSORBLOCK_SERVERS) {
            try {

                var resp = RequestUtils.sendGetRaw(url + "/api/skipSegments/" + URLUtils.silentEncode(hash.substring(0, 4))
                        + "?categories=" + URLUtils.silentEncode(categories)).get();

                if (resp.status() == 200) {
                    var any = mapper.readTree(resp.body());

                    for (var element : any) {
                        if (element.get("videoID").asText().equalsIgnoreCase(id)) {
                            return mapper.writeValueAsString(element);
                        }
                    }

                    return "{}";
                }
            } catch (Exception ignored) {
            }
        }

        ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("All SponsorBlock servers are down"));

        return null;
    }

}
