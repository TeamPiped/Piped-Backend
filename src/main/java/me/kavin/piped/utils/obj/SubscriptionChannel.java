package me.kavin.piped.utils.obj;

public class SubscriptionChannel {

    public String url, name, avatar;

    public boolean verified;

    public SubscriptionChannel(String url, String name, String avatar, boolean verified) {
        this.url = url;
        this.name = name;
        this.avatar = avatar;
        this.verified = verified;
    }
}
