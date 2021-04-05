package me.kavin.piped.utils.obj.db;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

@Entity
@Table(name = "users", indexes = { @Index(columnList = "id", name = "id_idx"),
        @Index(columnList = "username", name = "username_idx") })
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

    @ElementCollection
    @CollectionTable(name = "users_subscribed", joinColumns = @JoinColumn(name = "subscriber"), indexes = @Index(columnList = "subscriber", name = "subscriber_idx"))
    @Column(name = "channel", length = 30)
    private List<String> subscribed_ids;

    public User() {
    }

    public User(String username, String password, List<String> subscribed_ids) {
        this.username = username;
        this.password = password;
        this.subscribed_ids = subscribed_ids;
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

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getSubscribed() {
        return subscribed_ids;
    }

    public void setSubscribed(List<String> subscribed_ids) {
        this.subscribed_ids = subscribed_ids;
    }
}
