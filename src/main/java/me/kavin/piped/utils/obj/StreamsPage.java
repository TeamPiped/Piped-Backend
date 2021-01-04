package me.kavin.piped.utils.obj;

import java.util.List;

public class StreamsPage {

    public String nextpage;
    public List<StreamItem> relatedStreams;

    public StreamsPage(String nextpage, List<StreamItem> relatedStreams) {
	this.nextpage = nextpage;
	this.relatedStreams = relatedStreams;
    }
}
