package me.kavin.piped.utils.obj.db;

import java.util.Set;

import org.hibernate.annotations.Cascade;

import java.io.Serializable;

import jakarta.persistence.*;

@Entity
@Table(name = "oidc_user_data")
public class OidcUserData implements Serializable {

    public OidcUserData() {    
    }

    public OidcUserData(String sub, String username, String provider) {
        this.sub = sub;
        this.provider = provider;
        this.user = new User(username,"", Set.of());
    }

    @Column(name = "sub", unique = true, length = 255)
    @Id
    private String sub;

    @OneToOne
    @Cascade(org.hibernate.annotations.CascadeType.ALL)
    private User user;

    @Column(name = "provider", nullable = false)
    private String provider;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
