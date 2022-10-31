package me.kavin.piped.server.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentry.Sentry;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.ipfs.IPFS;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.*;
import me.kavin.piped.utils.obj.db.Video;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YouTubeChannelTabHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.CollectionUtils.collectRelatedItems;
import static me.kavin.piped.utils.URLUtils.rewriteURL;

public class ChannelHandlers {
    public static byte[] channelResponse(String channelPath) throws Exception {

        Sentry.setExtra("channelPath", channelPath);

        final ChannelInfo info = ChannelInfo.getInfo("https://youtube.com/" + channelPath);

        final List<ContentItem> relatedStreams = collectRelatedItems(info.getRelatedItems());

        Multithreading.runAsync(() -> {

            var channel = DatabaseHelper.getChannelFromId(info.getId());

            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                if (channel != null) {

                    boolean modified = false;

                    if (channel.isVerified() != info.isVerified()) {
                        channel.setVerified(info.isVerified());
                        modified = true;
                    }

                    if (!channel.getUploaderAvatar().equals(info.getAvatarUrl())) {
                        channel.setUploaderAvatar(info.getAvatarUrl());
                        modified = true;
                    }

                    if (!channel.getUploader().equals(info.getName())) {
                        channel.setUploader(info.getName());
                        modified = true;
                    }

                    if (modified) {
                        var tr = s.beginTransaction();
                        s.update(channel);
                        tr.commit();
                    }

                    Set<String> ids = info.getRelatedItems()
                            .stream()
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

                    for (StreamInfoItem item : info.getRelatedItems()) {
                        long time = item.getUploadDate() != null
                                ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                : System.currentTimeMillis();
                        if (System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
                            try {
                                String id = YOUTUBE_SERVICE.getStreamLHFactory().getId(item.getUrl());
                                var video = videos.stream()
                                        .filter(v -> v.getId().equals(id))
                                        .findFirst();
                                if (video.isPresent()) {
                                    VideoHelpers.updateVideo(s, video.get(), item);
                                } else {
                                    VideoHelpers.handleNewVideo("https://youtube.com/watch?v=" + id, time, channel);
                                }
                            } catch (Exception e) {
                                ExceptionHandler.handle(e);
                            }
                    }
                }
            }
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        List<ChannelTab> tabs = info.getTabs()
                .stream()
                .map(tab -> {
                    try {
                        return new ChannelTab(tab.getTab().name(), mapper.writeValueAsString(tab));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();

        final Channel channel = new Channel(info.getId(), info.getName(), rewriteURL(info.getAvatarUrl()),
                rewriteURL(info.getBannerUrl()), info.getDescription(), info.getSubscriberCount(), info.isVerified(),
                nextpage, relatedStreams, tabs);

        IPFS.publishData(channel);

        return mapper.writeValueAsBytes(channel);

    }

    public static byte[] channelPageResponse(String channelId, String prevpageStr)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(prevpageStr))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("nextpage is a required parameter"));

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        if (prevpage == null)
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("nextpage is a required parameter"));

        ListExtractor.InfoItemsPage<StreamInfoItem> info = ChannelInfo.getMoreItems(YOUTUBE_SERVICE,
                "https://youtube.com/channel/" + channelId, prevpage);

        final List<ContentItem> relatedStreams = collectRelatedItems(info.getItems());

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, relatedStreams);

        return mapper.writeValueAsBytes(streamspage);

    }

    public static byte[] channelTabResponse(String data)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(data))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("data is a required parameter"));

        YouTubeChannelTabHandler tabHandler = mapper.readValue(data, YouTubeChannelTabHandlerMixin.class);

        var info = ChannelTabInfo.getInfo(YOUTUBE_SERVICE, tabHandler);

        List<ContentItem> items = collectRelatedItems(info.getRelatedItems());

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        return mapper.writeValueAsBytes(new ChannelTabData(nextpage, items));
    }

    public static byte[] channelTabPageResponse(String data, String prevPageStr) throws Exception {

        if (StringUtils.isEmpty(data))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("data is a required parameter"));

        YouTubeChannelTabHandler tabHandler = mapper.readValue(data, YouTubeChannelTabHandlerMixin.class);

        Page prevPage = mapper.readValue(prevPageStr, Page.class);

        if (prevPage == null)
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("nextpage is a required parameter"));

        var info = ChannelTabInfo.getMoreItems(YOUTUBE_SERVICE, tabHandler, prevPage);

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        List<ContentItem> items = collectRelatedItems(info.getItems());

        return mapper.writeValueAsBytes(new ChannelTabData(nextpage, items));
    }
}
