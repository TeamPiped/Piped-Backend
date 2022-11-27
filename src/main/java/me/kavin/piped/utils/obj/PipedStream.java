package me.kavin.piped.utils.obj;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PipedStream {

    public String url, format, quality, mimeType, codec, audioTrackId, audioTrackName;
    public boolean videoOnly;

    public int bitrate, initStart, initEnd, indexStart, indexEnd, width, height, fps;

    public PipedStream(String url, String format, String quality, String mimeType, boolean videoOnly) {
        this.url = url;
        this.format = format;
        this.quality = quality;
        this.mimeType = mimeType;
        this.videoOnly = videoOnly;
    }

    public PipedStream(String url, String format, String quality, String mimeType, boolean videoOnly, int bitrate,
                       int initStart, int initEnd, int indexStart, int indexEnd, String codec, String audioTrackId, String audioTrackName) {
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
        this.audioTrackId = audioTrackId;
        this.audioTrackName = audioTrackName;
    }

    public PipedStream(String url, String format, String quality, String mimeType, boolean videoOnly, int bitrate,
                       int initStart, int initEnd, int indexStart, int indexEnd, String codec, int width, int height, int fps) {
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
    }
}
