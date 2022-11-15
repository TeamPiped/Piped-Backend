package me.kavin.piped.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.schabi.newpipe.extractor.Page;

import java.util.List;
import java.util.Map;

public abstract class PageMixin extends Page {

    @JsonCreator
    public PageMixin(@JsonProperty("url") String url, @JsonProperty("id") String id,
                     @JsonProperty("ids") List<String> ids, @JsonProperty("cookies") Map<String, String> cookies,
                     @JsonProperty("body") byte[] body) {
        super(url, id, ids, cookies, body);
    }
}
