package me.kavin.piped.utils.obj;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class SearchResults {

    public ObjectArrayList<Object> items;
    public String nextpage;

    public SearchResults(ObjectArrayList<Object> items, String nextpage) {
        this.nextpage = nextpage;
        this.items = items;
    }
}
