package me.kavin.piped.utils.obj;

public class StreamItem {

    public String url, title, thumbnail, uploaderName, uploaderUrl, uploaderAvatar, uploadedDate;
    public long duration, views, uploaded;
    public boolean uploaderVerified;

    public StreamItem(String url, String title, String thumbnail, String uploaderName, String uploaderUrl,
                      String uploaderAvatar, String uploadedDate, long duration, long views, long uploaded, boolean uploaderVerified) {
        this.url = url;
        this.title = title;
        this.thumbnail = thumbnail;
        this.uploaderName = uploaderName;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatar = uploaderAvatar;
        this.uploadedDate = uploadedDate;
        this.duration = duration;
        this.views = views;
        this.uploaded = uploaded;
        this.uploaderVerified = uploaderVerified;
    }
}
