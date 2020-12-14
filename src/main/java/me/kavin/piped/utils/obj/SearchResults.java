package me.kavin.piped.utils.obj;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.utils.obj.search.SearchItem;

public class SearchResults {

    public String nextpage, id;
    public ObjectArrayList<SearchItem> items;

    public SearchResults(String nextpage, String id, ObjectArrayList<SearchItem> items) {
	this.nextpage = nextpage;
	this.id = id;
	this.items = items;
    }
}
