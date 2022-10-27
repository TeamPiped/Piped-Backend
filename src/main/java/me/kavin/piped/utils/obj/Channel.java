package me.kavin.piped.utils.obj;

import java.util.List;

public class Channel {

    public String id, name, avatarUrl, bannerUrl, description, nextpage;
    public long subscriberCount;
    public boolean verified;
    public List<ContentItem> relatedStreams;

    public List<ChannelTab> tabs;

    public Channel(String id, String name, String avatarUrl, String bannerUrl, String description, long subscriberCount,
                   boolean verified, String nextpage, List<ContentItem> relatedStreams, List<ChannelTab> tabs) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.bannerUrl = bannerUrl;
        this.description = description;
        this.subscriberCount = subscriberCount;
        this.verified = verified;
        this.nextpage = nextpage;
        this.relatedStreams = relatedStreams;
        this.tabs = tabs;
    }
}
