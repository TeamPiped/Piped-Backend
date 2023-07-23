package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.PubSub;
import okhttp3.FormBody;
import okio.Buffer;
import org.hibernate.StatelessSession;
import rocks.kavin.reqwest4j.ReqwestUtils;
import rocks.kavin.reqwest4j.Response;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PubSubHelper {

    @Nullable
    public static CompletableFuture<Response> subscribePubSub(String channelId) throws IOException {

        if (!ChannelHelpers.isValidId(channelId))
            return null;

        PubSub pubsub = DatabaseHelper.getPubSubFromId(channelId);

        if (pubsub == null || System.currentTimeMillis() - pubsub.getSubbedAt() > TimeUnit.DAYS.toMillis(4)) {

            String callback = Constants.PUBSUB_URL + "/webhooks/pubsub";
            String topic = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId;

            var formBuilder = new FormBody.Builder();

            formBuilder.add("hub.callback", callback);
            formBuilder.add("hub.topic", topic);
            formBuilder.add("hub.verify", "async");
            formBuilder.add("hub.mode", "subscribe");
            formBuilder.add("hub.lease_seconds", "432000");

            if (pubsub == null)
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                    pubsub = new PubSub(channelId, -1);
                    var tr = s.beginTransaction();
                    s.insert(pubsub);
                    tr.commit();
                }

            // write form to read later
            var buffer = new Buffer();
            formBuilder.build().writeTo(buffer);

            var completableFuture = ReqwestUtils.fetch(Constants.PUBSUB_HUB_URL, "POST", buffer.readByteArray(), Map.of());

            completableFuture
                    .whenComplete((resp, e) -> {
                        if (e != null) {
                            ExceptionHandler.handle((Exception) e);
                            return;
                        }
                        if (resp != null && resp.status() != 202)
                            System.out.println("Failed to subscribe: " + resp.status() + "\n" + new String(resp.body()));
                    });

            return completableFuture;
        }
        return null;
    }

    public static void updatePubSub(String channelId) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var tr = s.beginTransaction();
            s.createNativeMutationQuery("INSERT INTO pubsub (id, subbed_at) VALUES (?, ?) " +
                            "ON CONFLICT (id) DO UPDATE SET subbed_at = excluded.subbed_at")
                    .setParameter(1, channelId)
                    .setParameter(2, System.currentTimeMillis())
                    .executeUpdate();
            tr.commit();
        }
    }
}
