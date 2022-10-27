package me.kavin.piped.utils.obj;

import me.kavin.piped.consts.Constants;

import java.util.List;

public class Streams {

    public String title, description, uploadDate, uploader, uploaderUrl, uploaderAvatar, thumbnailUrl, hls, dash,
            lbryId;

    public boolean uploaderVerified;

    public long duration, views, likes, dislikes, uploaderSubscriberCount;

    public List<PipedStream> audioStreams, videoStreams;

    public List<ContentItem> relatedStreams;

    public List<Subtitle> subtitles;

    public boolean livestream;

    public final String proxyUrl = Constants.PROXY_PART;

    public List<ChapterSegment> chapters;

    public Streams(String title, String description, String uploadDate, String uploader, String uploaderUrl,
                   String uploaderAvatar, String thumbnailUrl, long duration, long views, long likes, long dislikes, long uploaderSubscriberCount,
                   boolean uploaderVerified, List<PipedStream> audioStreams, List<PipedStream> videoStreams,
                   List<ContentItem> relatedStreams, List<Subtitle> subtitles, boolean livestream, String hls, String dash,
                   String lbryId, List<ChapterSegment> chapters) {
        this.title = title;
        this.description = description;
        this.uploadDate = uploadDate;
        this.uploader = uploader;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatar = uploaderAvatar;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.views = views;
        this.likes = likes;
        this.dislikes = dislikes;
        this.uploaderSubscriberCount = uploaderSubscriberCount;
        this.uploaderVerified = uploaderVerified;
        this.audioStreams = audioStreams;
        this.videoStreams = videoStreams;
        this.relatedStreams = relatedStreams;
        this.subtitles = subtitles;
        this.livestream = livestream;
        this.hls = hls;
        this.dash = dash;
        this.lbryId = lbryId;
        this.chapters = chapters;
    }
}
