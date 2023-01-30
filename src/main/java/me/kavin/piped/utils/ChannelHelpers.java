package me.kavin.piped.utils;

import static me.kavin.piped.utils.URLUtils.rewriteURL;

import com.google.errorprone.annotations.Var;
import com.rometools.rome.feed.synd.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.Video;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hibernate.StatelessSession;

public class ChannelHelpers {

    public static boolean isValidId(String id) {
        return !StringUtils.isBlank(id) && id.matches("UC[a-zA-Z\\d_-]{22}");
    }

    public static void updateChannel(StatelessSession s, Channel channel, String name, String avatarUrl, boolean uploaderVerified) {

        @Var boolean changed = false;

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

    public static SyndEntry createEntry(Video video, Channel channel) {
        SyndEntry entry = new SyndEntryImpl();
        SyndPerson person = new SyndPersonImpl();
        SyndContent content = new SyndContentImpl();
        SyndContent thumbnail = new SyndContentImpl();

        person.setName(channel.getUploader());
        person.setUri(Constants.FRONTEND_URL + "/channel/" + channel.getUploaderId());
        entry.setAuthors(Collections.singletonList(person));
        entry.setLink(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
        entry.setUri(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
        entry.setTitle(video.getTitle());
        entry.setPublishedDate(new Date(video.getUploaded()));

        String contentText = String.format("Title: %s\nViews: %d\nId: %s\nDuration: %d\nIs YT Shorts: %b", video.getTitle(), video.getViews(), video.getId(), video.getDuration(), video.isShort());
        content.setValue(contentText);

        String thumbnailContent =
                String.format("<div xmlns=\"http://www.w3.org/1999/xhtml\"><a href=\"%s\"><img src=\"%s\"/></a></div>",
                        Constants.FRONTEND_URL + "/watch?v=" + video.getId(),
                        StringEscapeUtils.escapeXml11(rewriteURL(video.getThumbnail()))
                );
        thumbnail.setType("xhtml");
        thumbnail.setValue(thumbnailContent);

        entry.setContents(List.of(thumbnail, content));

        return entry;
    }
}
