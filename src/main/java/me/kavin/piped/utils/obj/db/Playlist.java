package me.kavin.piped.utils.obj.db;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import jakarta.persistence.*;
import org.hibernate.annotations.Cascade;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "playlists", indexes = {@Index(columnList = "playlist_id", name = "playlists_playlist_id_idx"), @Index(columnList = "owner", name = "playlists_owner_idx")})
public class Playlist {

    public Playlist() {
    }

    public Playlist(String name, User owner, String thumbnail) {
        this.name = name;
        this.owner = owner;
        this.videos = new ObjectArrayList<>();
        this.thumbnail = thumbnail;
        this.playlist_id = UUID.randomUUID();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "playlist_id", unique = true, nullable = false)
    @GeneratedValue(generator = "UUID", strategy = GenerationType.IDENTITY)
    private UUID playlist_id;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "short_description", length = 100)
    private String short_description;

    @Column(name = "thumbnail", length = 300)
    private String thumbnail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    @ManyToMany
    @Column(name = "videos")
    @CollectionTable(name = "playlists_videos_ids")
    @OrderColumn(name = "videos_order")
    @Cascade(org.hibernate.annotations.CascadeType.PERSIST)
    private List<PlaylistVideo> videos;

    public long getId() {
        return id;
    }

    public UUID getPlaylistId() {
        return playlist_id;
    }

    public void setPlaylistId(UUID playlist_id) {
        this.playlist_id = playlist_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PlaylistVideo> getVideos() {
        return videos;
    }

    public void setVideos(List<PlaylistVideo> videos) {
        this.videos = videos;
    }

    public String getShortDescription() {
        return short_description;
    }

    public void setShortDescription(String short_description) {
        this.short_description = short_description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }
}
