package me.kavin.piped.utils.resp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListLinkHandlerMixin extends ListLinkHandler {

    @JsonCreator
    public ListLinkHandlerMixin(@JsonProperty("originalUrl") String originalUrl, @JsonProperty("url") String url, @JsonProperty("id") String id,
                                @JsonProperty("contentFilters") List<String> contentFilters, @JsonProperty("sortFilter") String sortFilter) {
        super(originalUrl, url, id, contentFilters, sortFilter);
    }
}
