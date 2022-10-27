package me.kavin.piped.utils.obj;

public class StreamItem extends ContentItem {

    public final String type = "stream";

    public String title, thumbnail, uploaderName, uploaderUrl, uploaderAvatar, uploadedDate, shortDescription;
    public long duration, views, uploaded;
    public boolean uploaderVerified, isShort;

    public StreamItem(String url, String title, String thumbnail, String uploaderName, String uploaderUrl,
                      String uploaderAvatar, String uploadedDate, String shortDescription, long duration, long views, long uploaded, boolean uploaderVerified, boolean isShort) {
        super(url);
        this.title = title;
        this.thumbnail = thumbnail;
        this.uploaderName = uploaderName;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatar = uploaderAvatar;
        this.uploadedDate = uploadedDate;
        this.shortDescription = shortDescription;
        this.duration = duration;
        this.views = views;
        this.uploaded = uploaded;
        this.uploaderVerified = uploaderVerified;
        this.isShort = isShort;
    }
}
