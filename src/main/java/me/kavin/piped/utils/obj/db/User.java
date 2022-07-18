package me.kavin.piped.utils.obj.db;

import jakarta.persistence.*;
import org.hibernate.annotations.Cascade;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {@Index(columnList = "id", name = "users_id_idx"),
        @Index(columnList = "username", name = "username_idx"),
        @Index(columnList = "session_id", name = "users_session_id_idx")})
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "username", unique = true, length = 24)
    private String username;

    @Column(name = "password", columnDefinition = "text")
    private String password;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @ElementCollection
    @CollectionTable(name = "users_subscribed", joinColumns = @JoinColumn(name = "subscriber", nullable = false),
            indexes = {@Index(columnList = "subscriber", name = "users_subscribed_subscriber_idx"),
                    @Index(columnList = "channel", name = "users_subscribed_channel_idx")})
    @Column(name = "channel", length = 30, nullable = false)
    @Cascade(org.hibernate.annotations.CascadeType.ALL)
    private Set<String> subscribed_ids;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL)
    private Set<Playlist> playlists;

    public User() {
    }

    public User(String username, String password, Set<String> subscribed_ids) {
        this.username = username;
        this.password = password;
        this.subscribed_ids = subscribed_ids;
        this.sessionId = String.valueOf(UUID.randomUUID());
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<String> getSubscribed() {
        return subscribed_ids;
    }

    public void setSubscribed(Set<String> subscribed_ids) {
        this.subscribed_ids = subscribed_ids;
    }

    public Set<Playlist> getPlaylists() {
        return playlists;
    }

    public void setPlaylists(Set<Playlist> playlists) {
        this.playlists = playlists;
    }
}
