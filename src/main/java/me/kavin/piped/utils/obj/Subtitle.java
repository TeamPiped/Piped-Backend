package me.kavin.piped.utils.obj;

public class Subtitle {

    private final String url, mimeType;

    public Subtitle(String url, String mimeType) {
	this.url = url;
	this.mimeType = mimeType;
    };

    public String getUrl() {
	return url;
    }

    public String getMimeType() {
	return mimeType;
    }
}
