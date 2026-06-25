package me.kavin.piped.utils;

import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Metadata;
import com.rometools.modules.mediarss.types.PlayerReference;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.synd.*;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.MatrixHelper;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.Video;
import me.kavin.piped.utils.obj.federation.FederatedChannelInfo;
import me.kavin.piped.utils.obj.federation.FederatedVideoInfo;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.utils.CollectionUtils.collectPreloadedTabs;
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

    public static void updateChannelVideos(ChannelInfo info, ChannelTabInfo tabInfo) {

        var channel = DatabaseHelper.getChannelFromId(info.getId());

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            if (channel != null) {

                updateChannel(s, channel, StringUtils.abbreviate(info.getName(), 100), info.getAvatars().isEmpty() ? null : info.getAvatars().getLast().getUrl(), info.isVerified());

                Set<String> ids = tabInfo.getRelatedItems()
                        .stream()
                        .filter(StreamInfoItem.class::isInstance)
                        .map(StreamInfoItem.class::cast)
                        .filter(item -> {
                            long time = item.getUploadDate() != null
                                    ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                    : System.currentTimeMillis();
                            return System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION);
                        })
                        .map(item -> {
                            try {
                                return YOUTUBE_SERVICE.getStreamLHFactory().getId(item.getUrl());
                            } catch (ParsingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .collect(Collectors.toUnmodifiableSet());

                List<Video> videos = DatabaseHelper.getVideosFromIds(s, ids);

                tabInfo.getRelatedItems()
                        .stream()
                        .filter(StreamInfoItem.class::isInstance)
                        .map(StreamInfoItem.class::cast).forEach(item -> {
                            var uploadDate = item.getUploadDate();
                            long timeForRetention = uploadDate != null
                                    ? uploadDate.offsetDateTime().toInstant().toEpochMilli()
                                    : System.currentTimeMillis();
                            if (System.currentTimeMillis() - timeForRetention < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
                                try {
                                    String id = YOUTUBE_SERVICE.getStreamLHFactory().getId(item.getUrl());
                                    var video = videos.stream()
                                            .filter(v -> v.getId().equals(id))
                                            .findFirst();
                                    if (video.isPresent()) {
                                        VideoHelpers.updateVideo(id, item);
                                    } else {
                                        VideoHelpers.handleNewVideo("https://youtube.com/watch?v=" + id,
                                                uploadDate != null ? timeForRetention : -1L, channel);
                                    }
                                } catch (Exception e) {
                                    ExceptionHandler.handle(e);
                                }
                        });
            }
        }
    }

    public static ChannelTabInfo videosTabInfo(ChannelInfo info) throws ExtractionException, IOException {
        return tabInfo(info, ChannelTabs.VIDEOS);
    }

    public static ChannelTabInfo shortsTabInfo(ChannelInfo info) throws ExtractionException, IOException {
        return tabInfo(info, ChannelTabs.SHORTS);
    }

    public static ChannelTabInfo livestreamsTabInfo(ChannelInfo info) throws ExtractionException, IOException {
        return tabInfo(info, ChannelTabs.LIVESTREAMS);
    }

    private static ChannelTabInfo tabInfo(ChannelInfo info, String tabId) throws ExtractionException, IOException {
        var tab = info.getTabs()
                .stream()
                .filter(t -> t.getContentFilters().contains(tabId))
                .findFirst();
        return tab.isPresent() ? ChannelTabInfo.getInfo(YOUTUBE_SERVICE, tab.get()) : null;
    }

    public static void refreshChannelTab(ChannelInfo info, ChannelTabInfo tabInfo) {
        if (tabInfo == null) return;
        Multithreading.runAsync(() -> federateChannelVideos(tabInfo));
        updateChannelVideos(info, tabInfo);
    }

    public static void federateChannelVideos(ChannelTabInfo tabInfo) {
        tabInfo.getRelatedItems()
                .stream().filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .forEach(infoItem -> {
                    if (
                            infoItem.getUploadDate() != null &&
                                    System.currentTimeMillis() - infoItem.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                            < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)
                    )
                        try {
                            MatrixHelper.sendEvent("video.piped.stream.info", new FederatedVideoInfo(
                                    StringUtils.substring(infoItem.getUrl(), -11), StringUtils.substring(infoItem.getUploaderUrl(), -24),
                                    infoItem.getName(),
                                    infoItem.getDuration(), infoItem.getViewCount())
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                });
    }

    public static void federateChannelInfo(ChannelInfo info) {
        try {
            MatrixHelper.sendEvent("video.piped.channel.info", new FederatedChannelInfo(
                    info.getId(), StringUtils.abbreviate(info.getName(), 100), info.getAvatars().isEmpty() ? null : info.getAvatars().getLast().getUrl(), info.isVerified())
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
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
