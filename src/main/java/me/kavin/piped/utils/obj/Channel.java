package me.kavin.piped.utils.obj;

import java.util.List;

public class Channel {

    public String id, name, avatarUrl, bannerUrl, description, nextpage;
    public List<StreamItem> relatedStreams;

    public Channel(String id, String name, String avatarUrl, String bannerUrl, String description, String nextpage,
            List<StreamItem> relatedStreams) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.bannerUrl = bannerUrl;
        this.description = description;
        this.nextpage = nextpage;
        this.relatedStreams = relatedStreams;
    }
}
