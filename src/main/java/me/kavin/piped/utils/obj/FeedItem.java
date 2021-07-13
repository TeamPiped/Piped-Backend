package me.kavin.piped.utils.obj;

public class FeedItem {

    public String id, title, thumbnail, uploader_id, uploader, uploaderAvatar;

    public long views, duration, uploaded;

    public boolean verified;

    public FeedItem(String id, String title, String thumbnail, String uploader_id, String uploader,
            String uploaderAvatar, long views, long duration, long uploaded, boolean verified) {
        this.id = id;
        this.title = title;
        this.thumbnail = thumbnail;
        this.uploader_id = uploader_id;
        this.uploader = uploader;
        this.uploaderAvatar = uploaderAvatar;
        this.views = views;
        this.duration = duration;
        this.uploaded = uploaded;
        this.verified = verified;
    }
}
