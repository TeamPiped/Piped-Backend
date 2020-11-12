package me.kavin.piped.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import me.kavin.piped.consts.Constants;

public class SponsorBlockUtils {

    public static final String getSponsors(String id, String categories)
	    throws IOException, InterruptedException, NoSuchAlgorithmException, JsonParserException {

	String hash = toSha256(id);

	URI uri = URI.create("https://sponsor.ajay.app/api/skipSegments/" + URLUtils.silentEncode(hash.substring(0, 4))
		+ "?categories=" + URLUtils.silentEncode(categories));

	JsonArray jArray = JsonParser.array()
		.from(Constants.h2client.send(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString()).body());

	jArray.removeIf(jObject -> !((JsonObject) jObject).getString("videoID").equalsIgnoreCase(id));

	return JsonWriter.string(jArray.getObject(0));
    }

    private static final String toSha256(final String videoId) throws NoSuchAlgorithmException {
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
