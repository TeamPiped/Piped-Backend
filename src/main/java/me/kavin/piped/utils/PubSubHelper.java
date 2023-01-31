package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.PubSub;
import okhttp3.FormBody;
import okio.Buffer;
import org.hibernate.StatelessSession;
import rocks.kavin.reqwest4j.ReqwestUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PubSubHelper {
    public static void subscribePubSub(String channelId) throws IOException {

        if (!ChannelHelpers.isValidId(channelId))
            return;

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

            var resp = ReqwestUtils.fetch(Constants.PUBSUB_HUB_URL, "POST", buffer.readByteArray(), Map.of());

            if (resp.status() != 202)
                System.out.println("Failed to subscribe: " + resp.status() + "\n" + new String(resp.body()));

        }
    }

    public static void updatePubSub(String channelId) {
        var pubsub = DatabaseHelper.getPubSubFromId(channelId);
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            s.beginTransaction();
            if (pubsub == null) {
                pubsub = new PubSub(channelId, System.currentTimeMillis());
                s.insert(pubsub);
            } else {
                pubsub.setSubbedAt(System.currentTimeMillis());
                s.update(pubsub);
            }
            s.getTransaction().commit();
        }
    }
}
