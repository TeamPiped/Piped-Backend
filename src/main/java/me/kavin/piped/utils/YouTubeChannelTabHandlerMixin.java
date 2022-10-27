package me.kavin.piped.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YouTubeChannelTabHandler;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YouTubeChannelTabHandlerMixin extends YouTubeChannelTabHandler {

    @JsonCreator
    @JsonIgnoreProperties(ignoreUnknown = true)
    public YouTubeChannelTabHandlerMixin(@JsonProperty("originalUrl") String originalUrl, @JsonProperty("url") String url,
                                         @JsonProperty("id") String id, @JsonProperty("contentFilters") List<String> contentFilters,
                                         @JsonProperty("sortFilter") String sortFilter, @JsonProperty("tab") ChannelTabHandler.Tab tab,
                                         @JsonProperty("visitorData") String visitorData) {
        super(new ListLinkHandler(originalUrl, url, id, contentFilters, sortFilter), tab, visitorData);
    }
}
