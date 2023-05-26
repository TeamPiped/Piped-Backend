package me.kavin.piped.utils.obj;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PipedStream {

    public String url, format, quality, mimeType, codec, audioTrackId, audioTrackName, audioTrackType, audioTrackLocale;
    public boolean videoOnly;

    public int bitrate, initStart, initEnd, indexStart, indexEnd, width, height, fps;

    public long contentLength;

    public PipedStream(String url, String format, String quality, String mimeType, boolean videoOnly, long contentLength) {
        this.url = url;
        this.format = format;
        this.quality = quality;
        this.mimeType = mimeType;
        this.videoOnly = videoOnly;
        this.contentLength = contentLength;
    }

    public PipedStream(String url, String format, String quality, String mimeType, boolean videoOnly, int bitrate,
                       int initStart, int initEnd, int indexStart, int indexEnd, long contentLength, String codec,
                       String audioTrackId, String audioTrackName, String audioTrackType, String audioTrackLocale) {
        this.url = url;
        this.format = format;
        this.quality = quality;
        this.mimeType = mimeType;
        this.videoOnly = videoOnly;
        this.bitrate = bitrate;
        this.initStart = initStart;
        this.initEnd = initEnd;
        this.indexStart = indexStart;
        this.indexEnd = indexEnd;
        this.contentLength = contentLength;
        this.codec = codec;
        this.audioTrackId = audioTrackId;
        this.audioTrackName = audioTrackName;
        this.audioTrackType = audioTrackType;
        this.audioTrackLocale = audioTrackLocale;
    }

    public PipedStream(String url, String format, String quality, String mimeType, boolean videoOnly, int bitrate,
                       int initStart, int initEnd, int indexStart, int indexEnd, String codec, int width, int height, int fps, long contentLength) {
        this.url = url;
        this.format = format;
        this.quality = quality;
        this.mimeType = mimeType;
        this.videoOnly = videoOnly;
        this.bitrate = bitrate;
        this.initStart = initStart;
        this.initEnd = initEnd;
        this.indexStart = indexStart;
        this.indexEnd = indexEnd;
        this.codec = codec;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.contentLength = contentLength;
    }
}
