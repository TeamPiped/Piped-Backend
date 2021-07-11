package me.kavin.piped.utils.obj.db;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

@Entity
@Table(name = "channels", indexes = { @Index(columnList = "uploader_id", name = "uploader_id_idx") })
public class Channel {

    @Id
    @Column(name = "uploader_id", length = 30)
    private String uploader_id;

    @Column(name = "uploader", length = 80)
    private String uploader;

    @Column(name = "uploader_avatar", length = 150)
    private String uploaderAvatar;

    @Column(name = "verified")
    private boolean verified;

    public Channel() {
    }

    public Channel(String uploader_id, String uploader, String uploaderAvatar, boolean verified) {
        this.uploader_id = uploader_id;
        this.uploader = uploader;
        this.uploaderAvatar = uploaderAvatar;
        this.verified = verified;
    }

    public String getUploaderId() {
        return uploader_id;
    }

    public void setUploaderId(String uploader_id) {
        this.uploader_id = uploader_id;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public String getUploaderAvatar() {
        return uploaderAvatar;
    }

    public void setUploaderAvatar(String uploaderAvatar) {
        this.uploaderAvatar = uploaderAvatar;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
