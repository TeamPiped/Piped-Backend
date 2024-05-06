package me.kavin.piped.utils.resp;

public class VideoResolvedResponse {

    public String videoId;
    public String start;
    public String end;

    public VideoResolvedResponse(String videoId, String start, String end) {
        this.videoId = videoId;
        this.start = start;
        this.end = end;
    }
}
