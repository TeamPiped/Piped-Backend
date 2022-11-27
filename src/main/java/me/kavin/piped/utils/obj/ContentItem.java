package me.kavin.piped.utils.obj;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "stream", value = StreamItem.class),
        @JsonSubTypes.Type(name = "channel", value = ChannelItem.class),
        @JsonSubTypes.Type(name = "playlist", value = PlaylistItem.class),
})
public abstract class ContentItem {

    public String url;

    public ContentItem(String url) {
        this.url = url;
    }
}
