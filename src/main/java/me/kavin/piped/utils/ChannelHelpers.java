package me.kavin.piped.utils;

import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Metadata;
import com.rometools.modules.mediarss.types.PlayerReference;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.synd.*;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.Video;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hibernate.StatelessSession;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static me.kavin.piped.utils.URLUtils.rewriteURL;

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
                final var host = url.getHost();
                if (!host.endsWith(".ggpht.com") && !host.endsWith(".googleusercontent.com"))
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

        String contentText = String.format("Title: %s\nViews: %d\nId: %s\nDuration: %s\nIs YT Shorts: %b", video.getTitle(), video.getViews(), video.getId(), DurationFormatUtils.formatDuration(video.getDuration() * 1000, "[HH]':'mm':'ss"), video.isShort());
        content.setValue(contentText);

        String thumbnailContent =
                String.format("<div xmlns=\"http://www.w3.org/1999/xhtml\"><a href=\"%s\"><img src=\"%s\"/></a></div>",
                        Constants.FRONTEND_URL + "/watch?v=" + video.getId(),
                        StringEscapeUtils.escapeXml11(rewriteURL(video.getThumbnail()))
                );
        thumbnail.setType("xhtml");
        thumbnail.setValue(thumbnailContent);

        entry.setContents(List.of(thumbnail, content));

        // the Media RSS content for embedding videos starts here
        // see https://www.rssboard.org/media-rss#media-content

        String playerUrl = Constants.FRONTEND_URL + "/embed/" + video.getId();
        MediaContent media = new MediaContent(new PlayerReference(URI.create(playerUrl)));
        media.setDuration(video.getDuration());

        Metadata metadata = new Metadata();
        metadata.setTitle(video.getTitle());
        Thumbnail metadataThumbnail = new Thumbnail(URI.create(video.getThumbnail()));
        metadata.setThumbnail(new Thumbnail[]{ metadataThumbnail });
        media.setMetadata(metadata);

        MediaEntryModuleImpl mediaModule = new MediaEntryModuleImpl();
        mediaModule.setMediaContents(new MediaContent[]{ media });
        entry.getModules().add(mediaModule);

        return entry;
    }

    public static void addChannelInformation(SyndFeed feed, Channel channel) {
        feed.setTitle("Piped - " + channel.getUploader());
        SyndImage channelIcon = new SyndImageImpl();
        channelIcon.setLink(Constants.FRONTEND_URL + "/channel/" + channel.getUploaderId());
        channelIcon.setTitle(channel.getUploader());
        channelIcon.setUrl(rewriteURL(channel.getUploaderAvatar()));
        feed.setIcon(channelIcon);
        feed.setImage(channelIcon);
    }
}
