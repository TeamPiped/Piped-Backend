package me.kavin.piped.utils.obj;

import com.fasterxml.jackson.databind.node.ArrayNode;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.RequestUtils;
import me.kavin.piped.utils.URLUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

import static me.kavin.piped.consts.Constants.*;
import static me.kavin.piped.utils.URLUtils.silentEncode;

public class MatrixHelper {

    public static final boolean UNAUTHENTICATED;
    public static final String MATRIX_TOKEN;
    public static final String ROOM_ID;

    public static final ArrayNode AUTHORIZED_USERS;

    static {
        UNAUTHENTICATED = StringUtils.isEmpty(Constants.MATRIX_TOKEN);

        try {

            if (UNAUTHENTICATED) {
                MATRIX_TOKEN = RequestUtils.getJsonNode(h2client, new Request.Builder()
                        .url(MATRIX_SERVER + "/_matrix/client/v3/register?kind=guest")
                        .post(RequestBody.create(mapper.writeValueAsBytes(
                                        mapper.createObjectNode().put("initial_device_display_name", "Piped's Backend")),
                                MediaType.get("application/json")))
                        .build()
                ).get("access_token").asText();
            } else {
                MATRIX_TOKEN = Constants.MATRIX_TOKEN;
            }

            if (UNAUTHENTICATED) {

                var joinResp = RequestUtils.getJsonNode(h2client, new Request.Builder()
                        .url(MATRIX_SERVER + "/_matrix/client/v3/directory/room/" + URLUtils.silentEncode(MATRIX_ROOM))
                        .header("Authorization", "Bearer " + MATRIX_TOKEN)
                        .build());

                try {
                    ROOM_ID = joinResp.get("room_id")
                            .asText();
                } catch (Exception e) {
                    System.err.println("Failed to join matrix room.");
                    System.err.println("Response: " + mapper.writeValueAsString(joinResp));
                    throw e;
                }
            } else {
                ROOM_ID = RequestUtils.getJsonNode(h2client, new Request.Builder()
                                .url(MATRIX_SERVER + "/_matrix/client/v3/join/" + URLUtils.silentEncode(MATRIX_ROOM))
                                .post(RequestBody.create(mapper.writeValueAsBytes(mapper.createObjectNode()), MediaType.get("application/json")))
                                .header("Authorization", "Bearer " + MATRIX_TOKEN)
                                .build())
                        .get("room_id")
                        .asText();
            }

            AUTHORIZED_USERS = (ArrayNode) RequestUtils.sendGetJson("https://raw.githubusercontent.com/TeamPiped/piped-federation/main/authorized-users.json").get();

        } catch (Exception e) {
            ExceptionHandler.handle(e);
            throw new RuntimeException(e);
        }
    }

    public static String sendEvent(String type, Object content) throws IOException {

        if (UNAUTHENTICATED)
            return null;

        return RequestUtils.getJsonNode(h2client, new Request.Builder()
                .url(MATRIX_SERVER + "/_matrix/client/v3/rooms/" + silentEncode(ROOM_ID) + "/send/" + type + "/" + RandomStringUtils.randomAlphanumeric(12))
                .header("Authorization", "Bearer " + MATRIX_TOKEN)
                .put(RequestBody.create(mapper.writeValueAsBytes(
                        mapper.createObjectNode()
                                .put("msgtype", type)
                                .set("content", mapper.valueToTree(content))
                ), MediaType.get("application/json")))
                .build()
        ).get("event_id").asText();
    }
}
