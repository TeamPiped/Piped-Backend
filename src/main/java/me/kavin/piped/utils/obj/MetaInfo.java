package me.kavin.piped.utils.obj;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.net.URL;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class MetaInfo {
    public String title, description;
    public List<URL> urls;
    public List<String> urlTexts;
}
