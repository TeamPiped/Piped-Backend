package me.kavin.piped.utils.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.MatrixHelper;
import me.kavin.piped.utils.obj.Streams;
import me.kavin.piped.utils.obj.federation.FederatedChannelInfo;
import me.kavin.piped.utils.obj.federation.FederatedGeoBypassRequest;
import me.kavin.piped.utils.obj.federation.FederatedGeoBypassResponse;
import me.kavin.piped.utils.obj.federation.FederatedVideoInfo;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.obj.MatrixHelper.*;

public class SyncRunner implements Runnable {

    private final OkHttpClient client;
    private final String url;
    private final String token;

    /**
     * @param client The OkHttpClient to use
     * @param url    The URL to send the request to
     * @param token  The access token to use requests.
     */
    public SyncRunner(OkHttpClient client, String url, String token) {
        this.client = client;
        this.url = url;
        this.token = token;
    }

    @Override
    public void run() {

        try {
            String user_id = null;

            if (!UNAUTHENTICATED) {
                // whoami to get the user id
                user_id = RequestUtils.getJsonNode(client, new Request.Builder()
                                .url(url + "/_matrix/client/v3/account/whoami")
                                .header("Authorization", "Bearer " + token)
                                .build())
                        .get("user_id")
                        .asText();
            }

            System.out.println("Logged in as user: " + user_id);

            // Join room and get the room id
            System.out.println("Room ID: " + ROOM_ID);

            String filter_id = null;

            // We have to filter on client-side if unauthenticated
            if (!UNAUTHENTICATED) {
                // Get the filter id
                filter_id = getFilterId(user_id, ROOM_ID);
            }

            System.out.println("Filter ID: " + filter_id);

            String next_batch = null;

            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    String url;

                    if (UNAUTHENTICATED) {
                        url = this.url + "/_matrix/client/v3/events?room_id=" + URLUtils.silentEncode(ROOM_ID);
                    } else {
                        url = this.url + "/_matrix/client/v3/sync?filter=" + filter_id;
                    }

                    boolean initial_sync = next_batch == null;

                    if (initial_sync) {
                        url += "&timeout=0";
                    } else {
                        url += "&" + (UNAUTHENTICATED ? "from" : "since") + "=" + next_batch;
                        url += "&timeout=30000";
                    }

                    var response = RequestUtils.getJsonNode(client, new Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer " + token)
                            .build());

                    Set<JsonNode> events;

                    if (UNAUTHENTICATED) {
                        events = StreamSupport.stream(response.get("chunk").spliterator(), true)
                                .filter(event -> event.get("type").asText().startsWith("video.piped."))
                                .filter(event -> {
                                    var sender = event.get("sender").asText();
                                    for (var user : AUTHORIZED_USERS)
                                        if (user.asText().equals(sender))
                                            return true;
                                    return false;
                                })
                                .collect(Collectors.toUnmodifiableSet());
                    } else {
                        var resp_events = response.at("/rooms/join/" + ROOM_ID + "/timeline").get("events");
                        if (resp_events != null) {
                            events = StreamSupport.stream(resp_events.spliterator(), true)
                                    .collect(Collectors.toUnmodifiableSet());
                        } else {
                            events = Set.of();
                        }
                    }

                    if (!initial_sync && events.size() > 0) {

                        for (var event : events) {

                            var type = event.get("type").asText();
                            var content = event.at("/content/content");

                            if (!UNAUTHENTICATED && type.startsWith("video.piped.stream.bypass.")) {
                                switch (type) {
                                    case "video.piped.stream.bypass.request" -> {
                                        if (Constants.YOUTUBE_COUNTRY == null) {
                                            continue;
                                        }
                                        FederatedGeoBypassRequest bypassRequest = mapper.treeToValue(content, FederatedGeoBypassRequest.class);
                                        if (bypassRequest.getAllowedCountries().contains(Constants.YOUTUBE_COUNTRY)) {
                                            // We're capable of helping another instance!
                                            Multithreading.runAsync(() -> {
                                                try {
                                                    StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=" + bypassRequest.getVideoId());

                                                    Streams streams = CollectionUtils.collectStreamInfo(info);

                                                    FederatedGeoBypassResponse bypassResponse = new FederatedGeoBypassResponse(bypassRequest.getVideoId(), Constants.YOUTUBE_COUNTRY, streams);

                                                    MatrixHelper.sendEvent("video.piped.stream.bypass.response", bypassResponse);

                                                } catch (Exception ignored) {
                                                }
                                            });
                                            continue;
                                        }
                                    }
                                    case "video.piped.stream.bypass.response" -> {
                                        FederatedGeoBypassResponse bypassResponse = mapper.treeToValue(content, FederatedGeoBypassResponse.class);
                                        GeoRestrictionBypassHelper.addResponse(bypassResponse);
                                        continue;
                                    }
                                }
                            }

                            if (event.get("sender").asText().equals(user_id)) {
                                continue;
                            }


                            switch (type) {
                                case "video.piped.stream.info" -> {
                                    FederatedVideoInfo info = mapper.treeToValue(content, FederatedVideoInfo.class);
                                    Multithreading.runAsync(() -> {
                                        if (!VideoHelpers.updateVideo(info.getVideoId(),
                                                info.getViews(),
                                                info.getDuration(),
                                                info.getTitle())) {
                                            var channel = DatabaseHelper.getChannelFromId(info.getUploaderId());
                                            if (channel != null)
                                                VideoHelpers.handleNewVideo("https://www.youtube.com/watch?v=" + info.getVideoId(),
                                                        System.currentTimeMillis(), channel);
                                        }

                                    });
                                }
                                case "video.piped.channel.info" -> {
                                    FederatedChannelInfo info = mapper.treeToValue(content, FederatedChannelInfo.class);
                                    Multithreading.runAsync(() -> {
                                        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                                            var channel = DatabaseHelper.getChannelFromId(s, info.getId());
                                            if (channel != null)
                                                ChannelHelpers.updateChannel(s, channel,
                                                        info.getName(),
                                                        info.getUploaderUrl(),
                                                        info.isVerified());
                                        }
                                    });
                                }
                                default -> System.err.println("Unknown event type: " + type);
                            }
                        }
                    }

                    next_batch = UNAUTHENTICATED ?
                            response.get("end").asText() :
                            response.get("next_batch").asText();

                } catch (Exception ignored) {
                    ignored.printStackTrace();
                    Thread.sleep(1000);
                }
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String getFilterId(String user_id, String room_id) throws IOException {

        var root = mapper.createObjectNode();

        var room = root.putObject("room");
        var timeline = room
                .putObject("timeline")
                .put("lazy_load_members", true)
                .put("limit", 50);

        room.putArray("rooms").add(room_id);
        timeline.set("senders", AUTHORIZED_USERS);

        timeline.putArray("types").add("video.piped.*");

        root.putObject("account_data").putArray("not_types").add("*");
        root.putObject("presence").putArray("not_types").add("*");
        room.putObject("account_data").put("lazy_load_members", true).putArray("not_types").add("*");
        room.putObject("ephemeral").put("lazy_load_members", true).putArray("not_types").add("*");
        room.putObject("state").put("lazy_load_members", true).putArray("not_types").add("*");

        // Create a filter
        return RequestUtils.getJsonNode(client, new Request.Builder()
                        .url(url + "/_matrix/client/v3/user/" + URLUtils.silentEncode(user_id) + "/filter")
                        .header("Authorization", "Bearer " + token)
                        .post(RequestBody.create(mapper.writeValueAsBytes(
                                root
                        ), MediaType.get("application/json")))
                        .build())
                .get("filter_id")
                .asText();
    }
}
