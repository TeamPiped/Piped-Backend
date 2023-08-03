package me.kavin.piped.utils.obj;

import lombok.NoArgsConstructor;
import me.kavin.piped.consts.Constants;

import java.util.List;

@NoArgsConstructor
public class Streams {

    public String title, description, uploadDate, uploader, uploaderUrl, uploaderAvatar, thumbnailUrl, hls, dash,
            lbryId, category, license, visibility;

    public List<String> tags;

    public List<MetaInfo> metaInfo;

    public boolean uploaderVerified;

    public long duration, views, likes, dislikes, uploaderSubscriberCount;

    public List<PipedStream> audioStreams, videoStreams;

    public List<ContentItem> relatedStreams;

    public List<Subtitle> subtitles;

    public boolean livestream;

    public final String proxyUrl = Constants.PROXY_PART;

    public List<ChapterSegment> chapters;

    public List<PreviewFrames> previewFrames;

    public Streams(String title, String description, String uploadDate, String uploader, String uploaderUrl,
                   String uploaderAvatar, String thumbnailUrl, long duration, long views, long likes, long dislikes, long uploaderSubscriberCount,
                   boolean uploaderVerified, List<PipedStream> audioStreams, List<PipedStream> videoStreams,
                   List<ContentItem> relatedStreams, List<Subtitle> subtitles, boolean livestream, String hls, String dash,
                   String lbryId, String category, String license, String visibility, List<String> tags, List<MetaInfo> metaInfo,
                   List<ChapterSegment> chapters, List<PreviewFrames> previewFrames) {
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
        this.previewFrames = previewFrames;
        this.category = category;
        this.license = license;
        this.tags = tags;
        this.metaInfo = metaInfo;
        this.visibility = visibility;
    }
}
