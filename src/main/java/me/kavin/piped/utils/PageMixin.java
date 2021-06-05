package me.kavin.piped.utils;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class PageMixin {

    @JsonCreator
    public PageMixin(@JsonProperty("url") String url, @JsonProperty("id") String id,
            @JsonProperty("ids") List<String> ids, @JsonProperty("cookies") Map<String, String> cookies,
            @JsonProperty("body") byte[] body) {
    }
}
