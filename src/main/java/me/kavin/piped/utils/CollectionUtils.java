package me.kavin.piped.utils;

import me.kavin.piped.utils.obj.ChannelItem;
import me.kavin.piped.utils.obj.ContentItem;
import me.kavin.piped.utils.obj.PlaylistItem;
import me.kavin.piped.utils.obj.StreamItem;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

import static me.kavin.piped.utils.URLUtils.rewriteURL;
import static me.kavin.piped.utils.URLUtils.substringYouTube;

public class CollectionUtils {

    public static List<ContentItem> collectRelatedItems(List<? extends InfoItem> items) {
        return items
                .stream()
                .parallel()
                .map(item -> {
                    if (item instanceof StreamInfoItem) {
                        return collectRelatedStream(item);
                    } else if (item instanceof PlaylistInfoItem) {
                        return collectRelatedPlaylist(item);
                    } else if (item instanceof ChannelInfoItem) {
                        return collectRelatedChannel(item);
                    } else {
                        throw new RuntimeException(
                                "Unknown item type: " + item.getClass().getName());
                    }
                }).toList();
    }

    private static StreamItem collectRelatedStream(Object o) {

        StreamInfoItem item = (StreamInfoItem) o;

        return new StreamItem(substringYouTube(item.getUrl()), item.getName(),
                rewriteURL(item.getThumbnailUrl()),
                item.getUploaderName(), substringYouTube(item.getUploaderUrl()),
                rewriteURL(item.getUploaderAvatarUrl()), item.getTextualUploadDate(),
                item.getShortDescription(), item.getDuration(),
                item.getViewCount(), item.getUploadDate() != null ?
                item.getUploadDate().offsetDateTime().toInstant().toEpochMilli() : -1,
                item.isUploaderVerified(), item.isShortFormContent());
    }

    private static PlaylistItem collectRelatedPlaylist(Object o) {

        PlaylistInfoItem item = (PlaylistInfoItem) o;

        return new PlaylistItem(substringYouTube(item.getUrl()), item.getName(),
                rewriteURL(item.getThumbnailUrl()),
                item.getUploaderName(), substringYouTube(item.getUploaderUrl()),
                item.isUploaderVerified(),
                item.getPlaylistType().name(), item.getStreamCount());
    }

    private static ChannelItem collectRelatedChannel(Object o) {

        ChannelInfoItem item = (ChannelInfoItem) o;

        return new ChannelItem(substringYouTube(item.getUrl()), item.getName(),
                rewriteURL(item.getThumbnailUrl()),
                item.getDescription(), item.getSubscriberCount(), item.getStreamCount(),
                item.isVerified());
    }
}
