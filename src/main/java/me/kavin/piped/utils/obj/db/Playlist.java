package me.kavin.piped.utils.obj.db;

import java.util.List;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "playlists", indexes = { @Index(columnList = "playlist_id", name = "playlists_playlist_id_idx") })
public class Playlist {

    public Playlist() {
    }

    public Playlist(String name, List<PlaylistVideo> videos, String thumbnail) {
        this.name = name;
        this.videos = videos;
        this.thumbnail = thumbnail;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "playlist_id")
    private UUID playlist_id;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "thumbnail", length = 255)
    private String thumbnail;

    @Column(name = "owner")
    private long owner;

    @ElementCollection(fetch = FetchType.LAZY)
    @OneToMany(targetEntity = PlaylistVideo.class)
    @Column(name = "videos")
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

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public long getOwner() {
        return owner;
    }

    public void setOwner(long owner) {
        this.owner = owner;
    }
}
