package me.kavin.piped.utils.obj;

public class PipedStream {

    public String url, format, quality, mimeType;
    public boolean videoOnly;

    public PipedStream(String url, String format, String quality, String mimeType, boolean videoOnly) {
	this.url = url;
	this.format = format;
	this.quality = quality;
	this.mimeType = mimeType;
	this.videoOnly = videoOnly;
    }
}
