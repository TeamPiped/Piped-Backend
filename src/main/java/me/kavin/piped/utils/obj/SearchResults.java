package me.kavin.piped.utils.obj;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class SearchResults {

    public ObjectArrayList<Object> items;
    public String nextpage, suggestion;
    public boolean corrected;

    public SearchResults(ObjectArrayList<Object> items, String nextpage) {
        this.nextpage = nextpage;
        this.items = items;
    }

    public SearchResults(ObjectArrayList<Object> items, String nextpage, String suggestion, boolean corrected) {
        this.items = items;
        this.nextpage = nextpage;
        this.suggestion = suggestion;
        this.corrected = corrected;
    }
}
