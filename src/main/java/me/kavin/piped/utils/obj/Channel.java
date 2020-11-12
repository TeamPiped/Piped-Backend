package me.kavin.piped.utils.obj;

import java.util.List;

public class Channel {

    private String name, avatarUrl, bannerUrl, description;
    private List<StreamItem> relatedStreams;

    public Channel(String name, String avatarUrl, String bannerUrl, String description,
	    List<StreamItem> relatedStreams) {
	this.name = name;
	this.avatarUrl = avatarUrl;
	this.description = description;
	this.relatedStreams = relatedStreams;
    }

    public String getName() {
	return name;
    }

    public String getAvatarUrl() {
	return avatarUrl;
    }

    public String getBannerUrl() {
	return bannerUrl;
    }

    public String getDescription() {
	return description;
    }

    public List<StreamItem> getRelatedStreams() {
	return relatedStreams;
    }
}
