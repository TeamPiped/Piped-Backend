package me.kavin.piped.utils.obj.federation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FederatedChannelInfo {

    private String id;
    private String name;
    private String uploaderUrl;
    private boolean verified;

    public FederatedChannelInfo() {
    }

    public FederatedChannelInfo(String id, String name, String uploaderUrl, boolean verified) {
        this.id = id;
        this.name = name;
        this.uploaderUrl = uploaderUrl;
        this.verified = verified;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUploaderUrl() {
        return uploaderUrl;
    }

    public boolean isVerified() {
        return verified;
    }
}
