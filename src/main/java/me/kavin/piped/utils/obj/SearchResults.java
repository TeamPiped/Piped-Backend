package me.kavin.piped.utils.obj;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.utils.obj.search.SearchItem;

public class SearchResults {

    public ObjectArrayList<SearchItem> items;
    public String nextpage;

    public SearchResults(ObjectArrayList<SearchItem> items, String nextpage) {
        this.nextpage = nextpage;
        this.items = items;
    }
}
