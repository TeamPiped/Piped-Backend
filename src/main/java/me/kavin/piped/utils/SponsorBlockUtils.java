package me.kavin.piped.utils;

import com.grack.nanojson.*;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SponsorBlockUtils {

    public static String getSponsors(String id, String categories)
            throws IOException, NoSuchAlgorithmException, JsonParserException {

        if (StringUtils.isEmpty(categories))
            return Constants.mapper.writeValueAsString(new InvalidRequestResponse());

        String hash = toSha256(id);

        JsonArray jArray = JsonParser.array().from(
                RequestUtils.sendGet("https://sponsor.ajay.app/api/skipSegments/" + URLUtils.silentEncode(hash.substring(0, 4))
                        + "?categories=" + URLUtils.silentEncode(categories))
        );

        jArray.removeIf(jObject -> !((JsonObject) jObject).getString("videoID").equalsIgnoreCase(id));

        return JsonWriter.string(jArray.getObject(0));
    }

    private static String toSha256(final String videoId) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] bytes = digest.digest(videoId.getBytes(StandardCharsets.UTF_8));
        final StringBuilder sb = new StringBuilder();

        for (final byte b : bytes) {
            final String hex = Integer.toHexString(0xff & b);

            if (hex.length() == 1) {
                sb.append('0');
            }

            sb.append(hex);
        }

        return sb.toString();
    }
}
