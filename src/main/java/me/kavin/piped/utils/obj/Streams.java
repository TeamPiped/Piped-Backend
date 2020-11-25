package me.kavin.piped.utils.obj;

import java.util.List;

public class Streams {

    private String title, description, uploadDate, uploader, uploaderUrl, uploaderAvatar, thumbnailUrl, hls;

    private long duration, views, likes, dislikes;

    private List<PipedStream> audioStreams, videoStreams;

    private List<StreamItem> relatedStreams;

    private List<Subtitle> subtitles;

    private boolean livestream;

    public Streams(String title, String description, String uploadDate, String uploader, String uploaderUrl,
	    String uploaderAvatar, String thumbnailUrl, long duration, long views, long likes, long dislikes,
	    List<PipedStream> audioStreams, List<PipedStream> videoStreams, List<StreamItem> relatedStreams,
	    List<Subtitle> subtitles, boolean livestream, String hls) {
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
    }

    public String getTitle() {
	return title;
    }

    public String getDescription() {
	return description;
    }

    public String getUploadDate() {
	return uploadDate;
    }

    public String getUploader() {
	return uploader;
    }

    public String getUploaderUrl() {
	return uploaderUrl;
    }

    public String getUploaderAvatar() {
	return uploaderAvatar;
    }

    public String getThumbnailUrl() {
	return thumbnailUrl;
    }

    public long getDuration() {
	return duration;
    }

    public long getViews() {
	return views;
    }

    public long getLikes() {
	return likes;
    }

    public long getDislikes() {
	return dislikes;
    }

    public List<PipedStream> getAudioStreams() {
	return audioStreams;
    }

    public List<PipedStream> getVideoStreams() {
	return videoStreams;
    }

    public List<StreamItem> getRelatedStreams() {
	return relatedStreams;
    }

    public List<Subtitle> getSubtitles() {
	return subtitles;
    }

    public boolean isLivestream() {
	return livestream;
    }

    public String getHls() {
	return hls;
    }
}
