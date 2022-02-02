package me.kavin.piped.utils.obj.db;

import javax.persistence.*;

@Entity
@Table(name = "videos", indexes = { @Index(columnList = "id", name = "videos_id_idx"),
        @Index(columnList = "uploader_id", name = "video_uploader_id_idx"),
        @Index(columnList = "uploaded", name = "video_uploaded_idx") })
public class Video {

    @Id
    @Column(name = "id", unique = true, length = 16)
    private String id;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "views")
    private long views;

    @Column(name = "duration")
    private long duration;

    @Column(name = "uploaded")
    private long uploaded;

    @Column(name = "thumbnail", length = 150)
    private String thumbnail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private Channel channel;

    public Video() {
    }

    public Video(String id, String title, long views, long duration, long uploaded, String thumbnail, Channel channel) {
        this.id = id;
        this.title = title;
        this.views = views;
        this.duration = duration;
        this.uploaded = uploaded;
        this.thumbnail = thumbnail;
        this.channel = channel;
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

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getUploaded() {
        return uploaded;
    }

    public void setUploaded(long uploaded) {
        this.uploaded = uploaded;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
