package me.kavin.piped.utils.obj;

public class Stream {

    private String url, format, quality, mimeType;

    public Stream(String url, String format, String quality, String mimeType) {
	this.url = url;
	this.format = format;
	this.quality = quality;
	this.mimeType = mimeType;
    }

    public String getUrl() {
	return url;
    }

    public String getFormat() {
	return format;
    }

    public String getQuality() {
	return quality;
    }

    public String getMimeType() {
	return mimeType;
    }
}
