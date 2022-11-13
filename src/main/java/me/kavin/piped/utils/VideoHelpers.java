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

        Video video = null;

        if (channel != null && (video = DatabaseHelper.getVideoFromId(info.getId())) == null
                && (System.currentTimeMillis() - infoTime) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {

            video = new Video(info.getId(), info.getName(), info.getViewCount(), info.getDuration(),
                    Math.max(infoTime, time), info.getThumbnailUrl(), info.isShortFormContent(), channel);

            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                var tr = s.beginTransaction();
                s.insert(video);
                tr.commit();
            }

        } else if (video != null) {
            updateVideo(info.getId(), info, time);
        }
    }

    public static void updateVideo(String id, StreamInfoItem item, long time, boolean addIfNotExistent) {
        Multithreading.runAsync(() -> {
            try {
                Video video = DatabaseHelper.getVideoFromId(id);

                if (video != null) {
                    try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                        updateVideo(s, video, item.getViewCount(), item.getDuration(), item.getName());
                    }
                } else if (addIfNotExistent) {
                    handleNewVideo("https://www.youtube.com/watch?v=" + id, time, null);
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    public static void updateVideo(String id, StreamInfo info, long time) {
        Multithreading.runAsync(() -> {
            try {
                Video video = DatabaseHelper.getVideoFromId(id);

                if (video != null) {
                    try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                        updateVideo(s, video, info.getViewCount(), info.getDuration(), info.getName());
                    }
                } else {
                    handleNewVideo(info, time, null);
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    public static void updateVideo(StatelessSession s, Video video, StreamInfoItem item) {
        updateVideo(s, video, item.getViewCount(), item.getDuration(), item.getName());
    }

    public static void updateVideo(StatelessSession s, Video video, long views, long duration, String title) {

        boolean changed = false;

        if (duration > 0 && video.getDuration() != duration) {
            video.setDuration(duration);
            changed = true;
        }
        if (!video.getTitle().equals(title)) {
            video.setTitle(title);
            changed = true;
        }
        if (views > video.getViews()) {
            video.setViews(views);
            changed = true;
        }

        if (changed) {
            var tr = s.beginTransaction();
            s.update(video);
            tr.commit();
        }
    }
}
