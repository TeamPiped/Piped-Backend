package me.kavin.piped.utils;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import me.kavin.piped.utils.resp.SimpleErrorMessage;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SponsorBlockUtils {

    public static String getSponsors(String id, String categories)
            throws IOException, NoSuchAlgorithmException {

        if (StringUtils.isEmpty(categories))
            return Constants.mapper.writeValueAsString(new InvalidRequestResponse());

        String hash = toSha256(id);

        for (String url : Constants.SPONSORBLOCK_SERVERS) {
            try (var resp = RequestUtils.sendGetRaw(url + "/api/skipSegments/" + URLUtils.silentEncode(hash.substring(0, 4))
                    + "?categories=" + URLUtils.silentEncode(categories))) {

                if (resp.code() == 200) {
                    JsonArray jArray = JsonParser.array().from(resp.body().string());

                    jArray.removeIf(jObject -> !((JsonObject) jObject).getString("videoID").equalsIgnoreCase(id));

                    return JsonWriter.string(jArray.getObject(0));
                }
            } catch (Exception ignored) {
            }
        }

        ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("All SponsorBlock servers are down"));

        return null;
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
