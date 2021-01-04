package me.kavin.piped.utils.obj;

public class StreamItem {

    public String url, title, thumbnail, uploaderName, uploaderUrl, uploadedDate;
    public long duration, views;

    public StreamItem(String url, String title, String thumbnail, String uploaderName, String uploaderUrl,
	    String uploadedDate, long duration, long views) {
	this.url = url;
	this.title = title;
	this.thumbnail = thumbnail;
	this.uploaderName = uploaderName;
	this.uploaderUrl = uploaderUrl;
	this.uploadedDate = uploadedDate;
	this.duration = duration;
	this.views = views;
    }
}
