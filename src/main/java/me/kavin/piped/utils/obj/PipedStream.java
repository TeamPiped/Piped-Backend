package me.kavin.piped.utils.obj;

public class PipedStream {

    public String url, format, quality, mimeType;

    public PipedStream(String url, String format, String quality, String mimeType) {
	this.url = url;
	this.format = format;
	this.quality = quality;
	this.mimeType = mimeType;
    }
}
