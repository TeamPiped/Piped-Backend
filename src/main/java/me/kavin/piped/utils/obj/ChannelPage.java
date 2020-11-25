package me.kavin.piped.utils.obj;

import java.util.List;

public class ChannelPage {

    private String nextpage;
    private List<StreamItem> relatedStreams;

    public ChannelPage(String nextpage, List<StreamItem> relatedStreams) {
	this.nextpage = nextpage;
	this.relatedStreams = relatedStreams;
    }

    public String getNextpage() {
	return nextpage;
    }

    public List<StreamItem> getRelatedStreams() {
	return relatedStreams;
    }
}
