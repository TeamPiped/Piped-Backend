package me.kavin.piped.utils.obj;

import java.util.List;

public class Streams {

    public String title, description, uploadDate, uploader, uploaderUrl, uploaderAvatar, thumbnailUrl, hls;

    public long duration, views, likes, dislikes;

    public List<PipedStream> audioStreams, videoStreams;

    public List<StreamItem> relatedStreams;

    public List<Subtitle> subtitles;

    public boolean livestream;

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
}
