package me.kavin.piped.utils.obj;

import java.util.List;

public class ChannelTabData {

    public String nextpage;
    public List<ContentItem> content;

    public ChannelTabData(String nextpage, List<ContentItem> content) {
        this.nextpage = nextpage;
        this.content = content;
    }
}
