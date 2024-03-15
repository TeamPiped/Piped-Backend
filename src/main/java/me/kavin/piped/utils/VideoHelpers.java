package me.kavin.piped.utils;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.Video;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredContentCountry;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredLocalization;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;

public class VideoHelpers {
    public static void handleNewVideo(String url, long time, me.kavin.piped.utils.obj.db.Channel channel) {
        try {
            var extractor = YOUTUBE_SERVICE.getStreamExtractor(url);
            extractor.fetchPage();
            handleNewVideo(extractor, time, channel);
        } catch (Exception e) {
            ExceptionHandler.handle(e);
        }
    }

    public static void handleNewVideo(StreamInfo info, long time, me.kavin.piped.utils.obj.db.Channel channel) throws Exception {

        if (channel == null)
            channel = DatabaseHelper.getChannelFromId(
                    info.getUploaderUrl().substring("https://www.youtube.com/channel/".length()));

        long infoTime = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        if (channel != null
                && (System.currentTimeMillis() - infoTime) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {

            info.setShortFormContent(isShort(info.getId()));

            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                if (!DatabaseHelper.doesVideoExist(s, info.getId())) {

                    Video video = new Video(info.getId(), info.getName(), info.getViewCount(), info.getDuration(),
                            Math.max(infoTime, time), info.getThumbnails().getLast().getUrl(), info.isShortFormContent(), channel);

                    insertVideo(video);
                    return;
                }
            }

            updateVideo(info.getId(), info, time);

        }
    }

    public static void handleNewVideo(StreamExtractor extractor, long time, me.kavin.piped.utils.obj.db.Channel channel) throws Exception {

        if (channel == null)
            channel = DatabaseHelper.getChannelFromId(
                    extractor.getUploaderUrl().substring("https://www.youtube.com/channel/".length()));

        long infoTime = Optional.ofNullable(extractor.getUploadDate())
                .map(date -> date.offsetDateTime().toInstant().toEpochMilli())
                .orElseGet(System::currentTimeMillis);

        if (channel != null
                && (System.currentTimeMillis() - infoTime) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {

            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                if (!DatabaseHelper.doesVideoExist(s, extractor.getId())) {

                    boolean isShort = extractor.isShortFormContent() || isShort(extractor.getId());

                    Video video = new Video(extractor.getId(), extractor.getName(), extractor.getViewCount(), extractor.getLength(),
                            Math.max(infoTime, time), extractor.getThumbnails().getLast().getUrl(), isShort, channel);

                    insertVideo(video);

                }
            }
        }

    }

    public static boolean isShort(String videoId) throws Exception {

        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(
                        getPreferredLocalization(), getPreferredContentCountry())
                        .value("url", "https://www.youtube.com/shorts/" + videoId)
                        .done())
                .getBytes(UTF_8);

        final JsonObject jsonResponse = getJsonPostResponse("navigation/resolve_url",
                body, getPreferredLocalization());

        return jsonResponse.getObject("endpoint").has("reelWatchEndpoint");
    }

    public static void updateVideo(String id, StreamInfo info, long time) {
        Multithreading.runAsync(() -> {
            try {
                if (!updateVideo(id, info.getViewCount(), info.getDuration(), info.getName())) {
                    var channel = DatabaseHelper.getChannelFromId(StringUtils.substring(info.getUploaderUrl(), -24));
                    if (channel != null)
                        handleNewVideo(info, time, channel);
                }
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    public static void updateVideo(String id, StreamInfoItem item) {
        updateVideo(id, item.getViewCount(), item.getDuration(), item.getName());
    }

    public static boolean updateVideo(String id, long views, long duration, String title) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            var cb = s.getCriteriaBuilder();
            var cu = cb.createCriteriaUpdate(Video.class);
            var root = cu.from(Video.class);
            cu.where(cb.equal(root.get("id"), id));


            if (duration > 0) {
                cu.set(root.get("duration"), duration);
            }
            if (title != null) {
                cu.set(root.get("title"), title);
            }
            if (views > 0) {
                cu.set(root.get("views"), views);
            }

            long updated;

            var tr = s.beginTransaction();
            try {
                updated = s.createMutationQuery(cu).executeUpdate();
                tr.commit();
            } catch (Exception e) {
                tr.rollback();

                // return true, so that we don't try to insert a video!
                return true;
            }

            return updated > 0;
        }
    }

    public static void insertVideo(Video video) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var tr = s.beginTransaction();
            try {
                s.createNativeMutationQuery(
                                "INSERT INTO videos (uploader_id,duration,is_short,thumbnail,title,uploaded,views,id) values " +
                                        "(:uploader_id,:duration,:is_short,:thumbnail,:title,:uploaded,:views,:id) ON CONFLICT (id) DO UPDATE SET " +
                                        "duration = excluded.duration, title = excluded.title, views = excluded.views"
                        )
                        .setParameter("uploader_id", video.getChannel().getUploaderId())
                        .setParameter("duration", video.getDuration())
                        .setParameter("is_short", video.isShort())
                        .setParameter("thumbnail", video.getThumbnail())
                        .setParameter("title", video.getTitle())
                        .setParameter("uploaded", video.getUploaded())
                        .setParameter("views", video.getViews())
                        .setParameter("id", video.getId())
                        .executeUpdate();
                tr.commit();
            } catch (Exception e) {
                tr.rollback();
                ExceptionHandler.handle(e);
            }
        }
    }
}
