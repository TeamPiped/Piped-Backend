package me.kavin.piped.utils;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import me.kavin.piped.utils.resp.SimpleErrorMessage;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class SponsorBlockUtils {

    public static String getSponsors(String id, String categories)
            throws IOException, NoSuchAlgorithmException {

        if (StringUtils.isEmpty(categories))
            return Constants.mapper.writeValueAsString(new InvalidRequestResponse());

        String hash = DigestUtils.sha256Hex(id);

        for (String url : Constants.SPONSORBLOCK_SERVERS) {
            try {

                var resp = RequestUtils.sendGetRaw(url + "/api/skipSegments/" + URLUtils.silentEncode(hash.substring(0, 4))
                        + "?categories=" + URLUtils.silentEncode(categories));

                if (resp.status() == 200) {
                    JsonArray jArray = JsonParser.array().from(new String(resp.body()));

                    jArray.removeIf(jObject -> !((JsonObject) jObject).getString("videoID").equalsIgnoreCase(id));

                    return JsonWriter.string(jArray.getObject(0));
                }
            } catch (Exception ignored) {
            }
        }

        ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("All SponsorBlock servers are down"));

        return null;
    }

}
