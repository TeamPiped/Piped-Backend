package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.MatrixHelper;
import me.kavin.piped.utils.obj.db.Video;
import me.kavin.piped.utils.obj.federation.FederatedVideoInfo;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.concurrent.TimeUnit;

public class VideoHelpers {
    public static void handleNewVideo(String url, long time, me.kavin.piped.utils.obj.db.Channel channel) {
        try {
            handleNewVideo(StreamInfo.getInfo(url), time, channel);
        } catch (Exception e) {
            ExceptionHandler.handle(e);
        }
    }

    public static void handleNewVideo(StreamInfo info, long time, me.kavin.piped.utils.obj.db.Channel channel) {

        Multithreading.runAsync(() -> {
            if (info.getUploadDate() != null && System.currentTimeMillis() - info.getUploadDate().offsetDateTime().toInstant().toEpochMilli() < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {
                try {
                    MatrixHelper.sendEvent("video.piped.stream.info", new FederatedVideoInfo(
                            StringUtils.substring(info.getUrl(), -11), StringUtils.substring(info.getUploaderUrl(), -24),
                            info.getName(),
                            info.getDuration(), info.getViewCount())
                    );
                } catch (Exception e) {
                    ExceptionHandler.handle(e);
                }
            }
        });

        if (channel == null)
            channel = DatabaseHelper.getChannelFromId(
                    info.getUploaderUrl().substring("https://www.youtube.com/channel/".length()));

        long infoTime = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        if (channel != null
                && (System.currentTimeMillis() - infoTime) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                if (!DatabaseHelper.doesVideoExist(s, info.getId())) {

                    Video video = new Video(info.getId(), info.getName(), info.getViewCount(), info.getDuration(),
                            Math.max(infoTime, time), info.getThumbnailUrl(), info.isShortFormContent(), channel);

                    var tr = s.beginTransaction();
                    s.insert(video);
                    tr.commit();
                    return;
                }
            }

            updateVideo(info.getId(), info, time);

        }
    }

    public static void updateVideo(String id, StreamInfoItem item, long time) {
        Multithreading.runAsync(() -> {
            try {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                    if (!updateVideo(s, id, item.getViewCount(), item.getDuration(), item.getName())) {
                        handleNewVideo(item.getUrl(), time, null);
                    }
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    public static void updateVideo(String id, StreamInfo info, long time) {
        Multithreading.runAsync(() -> {
            try {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                    if (!updateVideo(s, id, info.getViewCount(), info.getDuration(), info.getName())) {
                        handleNewVideo(info, time, null);
                    }
                }
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    public static void updateVideo(StatelessSession s, String id, StreamInfoItem item) {
        updateVideo(s, id, item.getViewCount(), item.getDuration(), item.getName());
    }

    public static boolean updateVideo(StatelessSession s, String id, long views, long duration, String title) {

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

        var tr = s.beginTransaction();
        long updated = s.createMutationQuery(cu).executeUpdate();
        tr.commit();

        return updated > 0;
    }
}
