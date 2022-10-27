package me.kavin.piped.utils.obj;

public class ChannelItem extends ContentItem {

    public final String type = "channel";

    public String name;
    public String thumbnail;
    public String description;
    public long subscribers, videos;
    public boolean verified;

    public ChannelItem(String url, String name, String thumbnail, String description, long subscribers, long videos,
                       boolean verified) {
        super(url);
        this.name = name;
        this.thumbnail = thumbnail;
        this.description = description;
        this.subscribers = subscribers;
        this.videos = videos;
        this.verified = verified;
    }
}
