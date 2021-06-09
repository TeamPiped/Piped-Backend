package me.kavin.piped.utils.obj.db;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Channel {

    @Id
    @Column(name = "uploader_id", length = 30)
    private String uploaderId;

    @Column(name = "uploader", length = 80)
    private String uploader;

    @Column(name = "uploader_avatar", length = 150)
    private String uploaderAvatar;

    @Column(name = "verified")
    private boolean verified;

}
