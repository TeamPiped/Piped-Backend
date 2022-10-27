package me.kavin.piped.utils.obj;

import java.util.List;

public class SearchResults {

    public List<ContentItem> items;
    public String nextpage, suggestion;
    public boolean corrected;

    public SearchResults(List<ContentItem> items, String nextpage) {
        this.nextpage = nextpage;
        this.items = items;
    }

    public SearchResults(List<ContentItem> items, String nextpage, String suggestion, boolean corrected) {
        this.items = items;
        this.nextpage = nextpage;
        this.suggestion = suggestion;
        this.corrected = corrected;
    }
}
