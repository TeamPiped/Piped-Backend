package me.kavin.piped.utils.obj;

import java.util.List;

import me.kavin.piped.consts.Constants;

public class Streams {

    public String title, description, uploadDate, uploader, uploaderUrl, uploaderAvatar, thumbnailUrl, hls, dash,
            lbryId;

    public long duration, views, likes, dislikes;

    public List<PipedStream> audioStreams, videoStreams;

    public List<StreamItem> relatedStreams;

    public List<Subtitle> subtitles;

    public boolean livestream;

    public final String proxyUrl = Constants.PROXY_PART;

    public Streams(String title, String description, String uploadDate, String uploader, String uploaderUrl,
            String uploaderAvatar, String thumbnailUrl, long duration, long views, long likes, long dislikes,
            List<PipedStream> audioStreams, List<PipedStream> videoStreams, List<StreamItem> relatedStreams,
            List<Subtitle> subtitles, boolean livestream, String hls, String dash, String lbryId) {
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
        this.audioStreams = audioStreams;
        this.videoStreams = videoStreams;
        this.relatedStreams = relatedStreams;
        this.subtitles = subtitles;
        this.livestream = livestream;
        this.hls = hls;
        this.dash = dash;
        this.lbryId = lbryId;
    }
}
