package me.kavin.piped.utils.obj.search;

public class SearchPlaylist extends SearchItem {

    private String uploaderName;
    private long videos;

    public SearchPlaylist(String name, String thumbnail, String url, String uploaderName, long videos) {
        super(name, thumbnail, url);
        this.uploaderName = uploaderName;
        this.videos = videos;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public long getVideos() {
        return videos;
    }
}
