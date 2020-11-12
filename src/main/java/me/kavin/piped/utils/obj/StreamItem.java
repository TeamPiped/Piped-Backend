package me.kavin.piped.utils.obj;

public class StreamItem {

    private String url, title, thumbnail, uploaderName, uploaderUrl;
    private long duration, views;

    public StreamItem(String url, String title, String thumbnail, String uploaderName, String uploaderUrl,
	    long duration, long views) {
	this.url = url;
	this.title = title;
	this.thumbnail = thumbnail;
	this.uploaderName = uploaderName;
	this.uploaderUrl = uploaderUrl;
	this.duration = duration;
	this.views = views;
    }

    public String getUrl() {
	return url;
    }

    public String getTitle() {
	return title;
    }

    public String getThumbnail() {
	return thumbnail;
    }

    public String getUploaderName() {
	return uploaderName;
    }

    public String getUploaderUrl() {
	return uploaderUrl;
    }

    public long getDuration() {
	return duration;
    }

    public long getViews() {
	return views;
    }
}
