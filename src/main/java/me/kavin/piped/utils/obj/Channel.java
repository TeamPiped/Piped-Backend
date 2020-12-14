package me.kavin.piped.utils.obj;

import java.util.List;

public class Channel {

    public String name, avatarUrl, bannerUrl, description, nextpage;
    public List<StreamItem> relatedStreams;

    public Channel(String name, String avatarUrl, String bannerUrl, String description, String nextpage,
	    List<StreamItem> relatedStreams) {
	this.name = name;
	this.avatarUrl = avatarUrl;
	this.description = description;
	this.nextpage = nextpage;
	this.relatedStreams = relatedStreams;
    }
}
