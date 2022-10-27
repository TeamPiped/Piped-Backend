package me.kavin.piped.utils.obj;

import java.util.List;

public class StreamsPage {

    public String nextpage;
    public List<ContentItem> relatedStreams;

    public StreamsPage(String nextpage, List<ContentItem> relatedStreams) {
        this.nextpage = nextpage;
        this.relatedStreams = relatedStreams;
    }
}
