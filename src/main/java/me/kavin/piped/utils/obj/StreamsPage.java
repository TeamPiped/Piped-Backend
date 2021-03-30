package me.kavin.piped.utils.obj;

import java.util.List;

public class StreamsPage {

    public String nextpage, nextid;
    public List<StreamItem> relatedStreams;

    public StreamsPage(String nextpage, String nextid, List<StreamItem> relatedStreams) {
        this.nextpage = nextpage;
        this.nextid = nextid;
        this.relatedStreams = relatedStreams;
    }
}
