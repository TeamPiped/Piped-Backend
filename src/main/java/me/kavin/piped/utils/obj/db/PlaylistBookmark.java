package me.kavin.piped.utils.obj.db;

import jakarta.persistence.*;

@Entity
@Table(name = "playlist_bookmarks", indexes = {@Index(columnList = "playlist_id", name = "playlist_bookmarks_playlist_id_idx"), @Index(columnList = "owner", name = "playlist_bookmarks_owner_idx")})
public class PlaylistBookmark {

    public PlaylistBookmark() {
    }

    public PlaylistBookmark(String playlist_id, String name, String short_description, String thumbnail_url, String uploader, String uploader_url, String uploader_avatar, long video_count, User owner) {
        this.playlist_id = playlist_id;
        this.name = name;
        this.short_description = short_description;
        this.thumbnail_url = thumbnail_url;
        this.uploader = uploader;
        this.uploader_url = uploader_url;
        this.uploader_avatar = uploader_avatar;
        this.video_count = video_count;
        this.owner = owner;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "playlist_id", nullable = false)
    private String playlist_id;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "short_description", length = 300)
    private String short_description;

    @Column(name = "thumbnail_url", length = 300)
    private String thumbnail_url;

    @Column(name = "uploader", length = 100)
    private String uploader;

    @Column(name = "uploader_url", length = 100)
    private String uploader_url;

    @Column(name = "uploader_avatar", length = 150)
    private String uploader_avatar;

    @Column(name = "video_count")
    private long video_count;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPlaylistId() {
        return playlist_id;
    }

    public void setPlaylistId(String playlist_id) {
        this.playlist_id = playlist_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortDescription() {
        return short_description;
    }

    public void setShortDescription(String short_description) {
        this.short_description = short_description;
    }

    public String getThumbnailUrl() {
        return thumbnail_url;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnail_url = thumbnailUrl;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public String getUploaderUrl() {
        return uploader_url;
    }

    public void setUploaderUrl(String uploaderUrl) {
        this.uploader_url = uploaderUrl;
    }

    public String getUploaderAvatar() {
        return uploader_avatar;
    }

    public void setUploaderAvatar(String uploaderAvatar) {
        this.uploader_avatar = uploaderAvatar;
    }

    public long getVideoCount() {
        return video_count;
    }

    public void setVideoCount(long videoCount) {
        this.video_count = videoCount;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }
}
