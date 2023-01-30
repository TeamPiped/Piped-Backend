package me.kavin.piped.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredContentCountry;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredLocalization;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;

import com.google.errorprone.annotations.Var;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import java.util.concurrent.TimeUnit;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.Video;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public class VideoHelpers {
    public static void handleNewVideo(String url, long time, Channel channel) {
        try {
            handleNewVideo(StreamInfo.getInfo(url), time, channel);
        } catch (Exception e) {
            ExceptionHandler.handle(e);
        }
    }

    public static void handleNewVideo(StreamInfo info, long time, @Var Channel channel) throws Exception {

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

                    var video = new Video(info.getId(), info.getName(), info.getViewCount(), info.getDuration(),
                            Math.max(infoTime, time), info.getThumbnailUrl(), info.isShortFormContent(), channel);

                    var tr = s.beginTransaction();
                    try {
                        s.insert(video);
                        tr.commit();
                    } catch (Exception e) {
                        tr.rollback();
                        ExceptionHandler.handle(e);
                    }
                    return;
                }
            }

            updateVideo(info.getId(), info, time);

        }
    }

    public static boolean isShort(String videoId) throws Exception {

         byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(
                        getPreferredLocalization(), getPreferredContentCountry())
                        .value("url", "https://www.youtube.com/shorts/" + videoId)
                        .done())
                .getBytes(UTF_8);

         JsonObject jsonResponse = getJsonPostResponse("navigation/resolve_url",
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
}
