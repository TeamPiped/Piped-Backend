package me.kavin.piped.utils.obj;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
public class PreviewFrames {

    public List<String> urls;
    public int frameWidth;
    public int frameHeight;
    public int totalCount;
    public int durationPerFrame;
    public int framesPerPageX;
    public int framesPerPageY;

}
