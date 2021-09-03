package me.kavin.piped.utils.obj;

public class StreamItem {

    public String url, title, thumbnail, uploaderName, uploaderUrl, uploaderAvatarUrl, uploadedDate;
    public long duration, views;
    public boolean uploaderVerified;

    public StreamItem(String url, String title, String thumbnail, String uploaderName, String uploaderUrl,
            String uploaderAvatarUrl, String uploadedDate, long duration, long views, boolean uploaderVerified) {
        this.url = url;
        this.title = title;
        this.thumbnail = thumbnail;
        this.uploaderName = uploaderName;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatarUrl = uploaderAvatarUrl;
        this.uploadedDate = uploadedDate;
        this.duration = duration;
        this.views = views;
        this.uploaderVerified = uploaderVerified;
    }
}
