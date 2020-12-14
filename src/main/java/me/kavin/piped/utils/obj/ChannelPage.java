package me.kavin.piped.utils.obj;

import java.util.List;

public class ChannelPage {

    public String nextpage;
    public List<StreamItem> relatedStreams;

    public ChannelPage(String nextpage, List<StreamItem> relatedStreams) {
	this.nextpage = nextpage;
	this.relatedStreams = relatedStreams;
    }
}
