package me.kavin.piped.utils.obj.search;

public class SearchStream extends SearchItem {

    private long views, duration;

    public SearchStream(String name, String thumbnail, String url, long views, long duration) {
	super(name, thumbnail, url);
	this.views = views;
	this.duration = duration;
    }

    public long getViews() {
	return views;
    }

    public long getDuration() {
	return duration;
    }
}
