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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

    public static CompletableFuture<ObjectNode> getDeArrowedInfo(List<String> videoIds) {
        ObjectNode objectNode = mapper.createObjectNode();

        var futures = videoIds.stream()
                .map(id -> getDeArrowedInfo(id).thenAcceptAsync(jsonNode -> objectNode.set(id, jsonNode.orElse(NullNode.getInstance()))))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApplyAsync(v -> objectNode, Multithreading.getCachedExecutor());
    }

    private static CompletableFuture<Optional<JsonNode>> getDeArrowedInfo(String videoId) {

        String hash = DigestUtils.sha256Hex(videoId);

        CompletableFuture<Optional<JsonNode>> future = new CompletableFuture<>();

        Multithreading.runAsync(() -> {
            for (String url : Constants.SPONSORBLOCK_SERVERS)
                try {
                    Optional<JsonNode> optional = RequestUtils.sendGetJson(url + "/api/branding/" + URLUtils.silentEncode(hash.substring(0, 4)))
                            .thenApplyAsync(json -> json.has(videoId) ? Optional.of(json.get(videoId)) : Optional.<JsonNode>empty())
                            .get();

                    optional.ifPresent(jsonNode -> {
                        ArrayNode nodes = (ArrayNode) jsonNode.get("thumbnails");
                        for (JsonNode node : nodes) {
                            if (!node.get("original").booleanValue())
                                ((ObjectNode) node).set("thumbnail", new TextNode(URLUtils.rewriteURL("https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=" + videoId + "&time=" + node.get("timestamp").asText())));
                        }
                    });


                    future.complete(optional);
                    return;
                } catch (Exception ignored) {
                }
            future.completeExceptionally(new Exception("All SponsorBlock servers are down"));
        });

        return future;

    }
}
