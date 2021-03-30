package me.kavin.piped.utils.obj.search;

public class SearchStream extends SearchItem {

    private String uploadDate;
    private long views, duration;

    public SearchStream(String name, String thumbnail, String url, String uploadDate, long views, long duration) {
        super(name, thumbnail, url);
        this.uploadDate = uploadDate;
        this.views = views;
        this.duration = duration;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public long getViews() {
        return views;
    }

    public long getDuration() {
        return duration;
    }
}
