package me.kavin.piped.utils;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider;
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult;
import rocks.kavin.reqwest4j.ReqwestUtils;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static me.kavin.piped.consts.Constants.mapper;

@RequiredArgsConstructor
public class BgPoTokenProvider implements PoTokenProvider {

    private final String bgHelperUrl;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private String getWebVisitorData() throws Exception {
        var html = RequestUtils.sendGet("https://www.youtube.com").get();
        var matcher = Pattern.compile("visitorData\":\"([\\w%-]+)\"").matcher(html);

        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new RuntimeException("Failed to get visitor data");
    }

    private final Queue<PoTokenResult> validPoTokens = new ConcurrentLinkedQueue<>();

    private PoTokenResult getPoTokenPooled() throws Exception {
        PoTokenResult poToken = validPoTokens.poll();

        if (poToken == null) {
            poToken = createWebClientPoToken();
        }

        // if still null, return null
        if (poToken == null) {
            return null;
        }

        // timer to insert back into queue after 10 + random seconds
        int delay = 10_000 + ThreadLocalRandom.current().nextInt(5000);
        PoTokenResult finalPoToken = poToken;
        scheduler.schedule(() -> validPoTokens.offer(finalPoToken), delay, TimeUnit.MILLISECONDS);

        return poToken;
    }

    private PoTokenResult createWebClientPoToken() throws Exception {
        String visitorDate = getWebVisitorData();

        String poToken = ReqwestUtils.fetch(bgHelperUrl + "/generate", "POST", mapper.writeValueAsBytes(mapper.createObjectNode().put(
                "visitorData", visitorDate
        )), Map.of(
                "Content-Type", "application/json"
        )).thenApply(response -> {
            try {
                return mapper.readTree(response.body()).get("poToken").asText();
            } catch (Exception e) {
                return null;
            }
        }).join();

        if (poToken != null) {
            return new PoTokenResult(visitorDate, poToken, null);
        }

        return null;
    }

    @Override
    public @Nullable PoTokenResult getWebClientPoToken(String videoId) {
        try {
            return getPoTokenPooled();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public @Nullable PoTokenResult getWebEmbedClientPoToken(String videoId) {
        return null;
    }

    @Override
    public @Nullable PoTokenResult getAndroidClientPoToken(String videoId) {
        return null;
    }

    @Override
    public @Nullable PoTokenResult getIosClientPoToken(String videoId) {
        return null;
    }
}
