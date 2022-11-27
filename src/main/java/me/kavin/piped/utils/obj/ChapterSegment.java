package me.kavin.piped.utils.obj;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ChapterSegment {

    public String title, image;
    public int start;

    public ChapterSegment(String title, String image, int start) {
        this.title = title;
        this.image = image;
        this.start = start;
    }
}
