package me.kavin.piped.utils.obj.db;

import jakarta.persistence.*;

@Entity
@Table(name = "playlist_videos", indexes = {@Index(columnList = "id", name = "playlist_videos_id_idx"),
        @Index(columnList = "uploader_id", name = "playlist_videos_uploader_id_idx")})
public class PlaylistVideo {

    public PlaylistVideo() {
    }

    public PlaylistVideo(String id, String title, String thumbnail, long duration, Channel channel) {
        this.id = id;
        this.title = title;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.channel = channel;
    }

    @Id
    @Column(name = "id", unique = true, length = 16, nullable = false)
    private String id;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "duration")
    private long duration;

    @Column(name = "thumbnail", length = 400)
    private String thumbnail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private Channel channel;

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

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
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
