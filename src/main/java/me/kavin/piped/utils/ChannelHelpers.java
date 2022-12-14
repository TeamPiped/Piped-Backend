package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.Channel;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ChannelHelpers {

    public static boolean isValidId(String id) {
        return !StringUtils.isBlank(id) && id.matches("UC[a-zA-Z\\d_-]{22}");
    }

    public static void updateChannel(StatelessSession s, Channel channel, String name, String avatarUrl, boolean uploaderVerified) {

        boolean changed = false;

        if (name != null && !name.equals(channel.getUploader())) {
            channel.setUploader(name);
            changed = true;
        }

        if (avatarUrl != null && !avatarUrl.equals(channel.getUploaderAvatar())) {

            URL url;
            try {
                url = new URL(avatarUrl);
                if (!url.getHost().endsWith(".ggpht.com"))
                    return;
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

            try (var resp = Constants.h2client.newCall(new Request.Builder().url(url).head().build()).execute()) {

                if (resp.isSuccessful())
                    channel.setUploaderAvatar(avatarUrl);

                changed = true;
            } catch (IOException e) {
                return;
            }
        }

        if (uploaderVerified != channel.isVerified()) {
            channel.setVerified(uploaderVerified);
            changed = true;
        }

        if (changed) {
            var tr = s.beginTransaction();
            s.update(channel);
            tr.commit();
        }
    }

}
