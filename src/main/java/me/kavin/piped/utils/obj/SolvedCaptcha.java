package me.kavin.piped.utils.obj;

import java.util.Map;

public class SolvedCaptcha {

    private Map<String, String> cookies;
    private String gRecaptchaResponse;

    public SolvedCaptcha(Map<String, String> cookies, String gRecaptchaResponse) {
        this.cookies = cookies;
        this.gRecaptchaResponse = gRecaptchaResponse;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public String getRecaptchaResponse() {
        return gRecaptchaResponse;
    }
}
