package me.kavin.piped.utils.obj;

import java.util.List;

public class StreamsPage {

    public String nextpage, nextbody;
    public List<StreamItem> relatedStreams;

    public StreamsPage(String nextpage, String nextbody, List<StreamItem> relatedStreams) {
        this.nextpage = nextpage;
        this.nextbody = nextbody;
        this.relatedStreams = relatedStreams;
    }
}
