package me.kavin.piped.utils.obj.db;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

@Entity
@Table(name = "videos", indexes = { @Index(columnList = "id", name = "id_idx") })
public class Video {

    @Id
    @Column(name = "id", unique = true, length = 16)
    private String id;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "views")
    private long views;

    @Column(name = "duration")
    private int duration;

    @Column(name = "uploaded")
    private long uploaded;

    @Column(name = "uploader", length = 80)
    private String uploader;

    @Column(name = "uploader_url", length = 30)
    private String uploaderUrl;

    @Column(name = "uploader_avatar", length = 150)
    private String uploaderAvatar;

    @Column(name = "verified")
    private boolean verified;

    @Column(name = "thumbnail", length = 150)
    private String thumbnail;

    public Video() {
    }

    public Video(String id, String title, long views, int duration, long uploaded, String uploader, String uploaderUrl,
            String uploaderAvatar, boolean verified, String thumbnail) {
        this.id = id;
        this.title = title;
        this.views = views;
        this.duration = duration;
        this.uploaded = uploaded;
        this.uploader = uploader;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatar = uploaderAvatar;
        this.verified = verified;
        this.thumbnail = thumbnail;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getViews() {
        return views;
    }

    public void setViews(long views) {
        this.views = views;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public long getUploaded() {
        return uploaded;
    }

    public void setUploaded(long uploaded) {
        this.uploaded = uploaded;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public String getUploaderUrl() {
        return uploaderUrl;
    }

    public void setUploaderUrl(String uploaderUrl) {
        this.uploaderUrl = uploaderUrl;
    }

    public String getUploaderAvatar() {
        return uploaderAvatar;
    }

    public void setUploaderAvatar(String uploaderAvatar) {
        this.uploaderAvatar = uploaderAvatar;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
}
