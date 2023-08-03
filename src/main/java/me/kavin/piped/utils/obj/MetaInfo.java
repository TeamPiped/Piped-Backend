package me.kavin.piped.utils.obj;

import java.util.List;
import java.net.URL;

public class MetaInfo {
    public String title, description;
    public List<URL> urls;
    public List<String> urlTexts;

    public MetaInfo(String title, String description, List<URL> urls, List<String> urlTexts) {
        this.title = title;
        this.description = description;
        this.urls = urls;
        this.urlTexts = urlTexts;
    }
}
