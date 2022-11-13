package me.kavin.piped.utils.obj.federation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FederatedVideoInfo {

    private String videoId, uploaderId, title;
    private long duration, views;

    public FederatedVideoInfo() {
    }

    public FederatedVideoInfo(String videoId, String uploaderId, String title, long duration, long views) {
        this.videoId = videoId;
        this.uploaderId = uploaderId;
        this.title = title;
        this.duration = duration;
        this.views = views;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public String getTitle() {
        return title;
    }

    public long getDuration() {
        return duration;
    }

    public long getViews() {
        return views;
    }
}
