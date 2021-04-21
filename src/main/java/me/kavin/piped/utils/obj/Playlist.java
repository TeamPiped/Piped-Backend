package me.kavin.piped.utils.obj;

import java.util.List;

public class Playlist {

    public String name, thumbnailUrl, bannerUrl, nextpage, nextbody, uploader, uploaderUrl, uploaderAvatar;
    public int videos;
    public List<StreamItem> relatedStreams;

    public Playlist(String name, String thumbnailUrl, String bannerUrl, String nextpage, String nextbody,
            String uploader, String uploaderUrl, String uploaderAvatar, int videos, List<StreamItem> relatedStreams) {
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.bannerUrl = bannerUrl;
        this.nextpage = nextpage;
        this.nextbody = nextbody;
        this.videos = videos;
        this.uploader = uploader;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatar = uploaderAvatar;
        this.relatedStreams = relatedStreams;
    }
}
