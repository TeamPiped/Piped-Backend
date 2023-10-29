package me.kavin.piped.utils.obj.db;

import jakarta.persistence.*;

@Entity
@Table(name = "oidc_user_data")
public class OidcUserData {

    @Column(unique = true)
    @Id
    private String sub;

    @OneToOne
    private User user;

    private String provider;


}
