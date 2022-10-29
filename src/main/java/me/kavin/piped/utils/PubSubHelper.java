package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.PubSub;
import okhttp3.FormBody;
import okhttp3.Request;
import org.hibernate.StatelessSession;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class PubSubHelper {
    public static void subscribePubSub(String channelId) throws IOException {

        PubSub pubsub = DatabaseHelper.getPubSubFromId(channelId);

        if (pubsub == null || System.currentTimeMillis() - pubsub.getSubbedAt() > TimeUnit.DAYS.toMillis(4)) {

            String callback = Constants.PUBSUB_URL + "/webhooks/pubsub";
            String topic = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId;

            var builder = new Request.Builder()
                    .url(Constants.PUBSUB_HUB_URL);

            var formBuilder = new FormBody.Builder();

            formBuilder.add("hub.callback", callback);
            formBuilder.add("hub.topic", topic);
            formBuilder.add("hub.verify", "async");
            formBuilder.add("hub.mode", "subscribe");
            formBuilder.add("hub.lease_seconds", "432000");

            try (var resp = Constants.h2client
                    .newCall(builder.post(formBuilder.build())
                            .build()).execute()) {

                if (resp.code() == 202) {
                    try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                        var tr = s.beginTransaction();
                        if (pubsub == null) {
                            pubsub = new PubSub(channelId, System.currentTimeMillis());
                            s.insert(pubsub);
                        } else {
                            pubsub.setSubbedAt(System.currentTimeMillis());
                            s.update(pubsub);
                        }
                        tr.commit();
                    }

                } else
                    System.out.println("Failed to subscribe: " + resp.code() + "\n" + Objects.requireNonNull(resp.body()).string());

            }
        }

    }
}
