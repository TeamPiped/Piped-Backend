package me.kavin.piped.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import me.kavin.piped.utils.resp.SimpleErrorMessage;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinTask;

import static me.kavin.piped.consts.Constants.SPONSORBLOCK_SERVERS;
import static me.kavin.piped.consts.Constants.mapper;

public class SponsorBlockUtils {

    public static String getSponsors(String id, String categories, String actionType)
            throws IOException {

        if (StringUtils.isEmpty(categories))
            return mapper.writeValueAsString(new InvalidRequestResponse());

        String hash = DigestUtils.sha256Hex(id);

        for (String apiUrl : Constants.SPONSORBLOCK_SERVERS) {
            try {
                String url = apiUrl + "/api/skipSegments/" + URLUtils.silentEncode(hash.substring(0, 4))

                        + "?categories=" + URLUtils.silentEncode(categories);
                if (actionType != null && !actionType.isBlank())
                    url += "&actionTypes=" + URLUtils.silentEncode(actionType);

                var resp = RequestUtils.sendGetRaw(url).get();

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

    public static CompletableFuture<ObjectNode> getDeArrowedInfo(String[] videoIds) {
        ObjectNode objectNode = mapper.createObjectNode();

        var futures = Arrays.stream(videoIds)
                .map(id -> getDeArrowedInfo(id, SPONSORBLOCK_SERVERS.toArray(new String[0]))
                        .thenAcceptAsync(jsonNode -> objectNode.set(id, jsonNode.orElse(NullNode.getInstance())))
                )
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApplyAsync(v -> objectNode, Multithreading.getCachedExecutor());
    }

    private static CompletableFuture<Optional<JsonNode>> getDeArrowedInfo(String videoId, String[] servers) {

        String hash = DigestUtils.sha256Hex(videoId);

        CompletableFuture<Optional<JsonNode>> future = new CompletableFuture<>();

        var task = ForkJoinTask.adapt(() -> {
            fetchDeArrowedCf(future, videoId, hash, servers);
        });

        Multithreading.runAsyncTask(task);

        return future;

    }

    private static final ObjectNode EMPTY_DEARROWED_INFO;

    static {
        EMPTY_DEARROWED_INFO = mapper.createObjectNode();
        EMPTY_DEARROWED_INFO.putArray("titles");
        EMPTY_DEARROWED_INFO.putArray("thumbnails");
        EMPTY_DEARROWED_INFO.set("videoDuration", NullNode.getInstance());
    }

    private static void fetchDeArrowedCf(CompletableFuture<Optional<JsonNode>> future, String videoId, String hash, String[] servers) {

        var completableFuture = RequestUtils.sendGetJson(servers[0] + "/api/branding/" + URLUtils.silentEncode(hash.substring(0, 4)))
                .thenApplyAsync(json -> json.has(videoId) ? Optional.of(json.get(videoId)) : Optional.<JsonNode>empty());

        completableFuture.thenAcceptAsync(optional -> optional.ifPresent(jsonNode -> {
            ArrayNode nodes = (ArrayNode) jsonNode.get("thumbnails");
            for (JsonNode node : nodes) {
                if (!node.get("original").booleanValue())
                    ((ObjectNode) node).set("thumbnail", new TextNode(URLUtils.rewriteURL("https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=" + videoId + "&time=" + node.get("timestamp").asText())));
            }
        }));

        completableFuture = completableFuture.thenApplyAsync(optional -> {
            if (optional.isEmpty()) {
                var clone = EMPTY_DEARROWED_INFO.deepCopy();
                clone.put("randomTime", new Alea(videoId).next());
                return Optional.of(clone);
            } else
                return optional;
        });


        completableFuture.whenComplete((optional, throwable) -> {
            if (throwable == null)
                future.complete(optional);
            else {
                if (servers.length == 1)
                    future.completeExceptionally(new Exception("All SponsorBlock servers are down"));
                else
                    fetchDeArrowedCf(future, videoId, hash, Arrays.copyOfRange(servers, 1, servers.length));
            }
        });
    }
}
