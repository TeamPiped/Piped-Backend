package me.kavin.piped.utils;

import com.grack.nanojson.JsonParserException;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.SolvedCaptcha;
import okhttp3.FormBody;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import rocks.kavin.reqwest4j.ReqwestUtils;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DownloaderImpl extends Downloader {

    private static HttpCookie saved_cookie;
    private static long cookie_received;
    private static final Object cookie_lock = new Object();

    /**
     * Executes a request with HTTP/2.
     */
    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {

        // TODO: HTTP/3 aka QUIC
        var bytes = request.dataToSend();
        Map<String, String> headers = new Object2ObjectOpenHashMap<>();

        if (saved_cookie != null && !saved_cookie.hasExpired())
            headers.put("Cookie", saved_cookie.getName() + "=" + saved_cookie.getValue());

        request.headers().forEach((name, values) -> values.forEach(value -> headers.put(name, value)));

        var resp = ReqwestUtils.fetch(request.url(), request.httpMethod(), bytes, headers);

        if (resp.status() == 429) {

            synchronized (cookie_lock) {

                if (saved_cookie != null && saved_cookie.hasExpired()
                        || (System.currentTimeMillis() - cookie_received > TimeUnit.MINUTES.toMillis(30)))
                    saved_cookie = null;

                String redir_url = String.valueOf(resp.finalUrl());

                if (saved_cookie == null && redir_url.startsWith("https://www.google.com/sorry")) {

                    var formBuilder = new FormBody.Builder();
                    String sitekey = null, data_s = null;

                    for (Element el : Jsoup.parse(new String(resp.body())).selectFirst("form").children()) {
                        String name;
                        if (!(name = el.tagName()).equals("script")) {
                            if (name.equals("input"))
                                formBuilder.add(el.attr("name"), el.attr("value"));
                            else if (name.equals("div") && el.attr("id").equals("recaptcha")) {
                                sitekey = el.attr("data-sitekey");
                                data_s = el.attr("data-s");
                            }
                        }
                    }
                    if (StringUtils.isEmpty(sitekey) || StringUtils.isEmpty(data_s))
                        throw new ReCaptchaException("Could not get recaptcha", redir_url);

                    SolvedCaptcha solved = null;

                    try {
                        solved = CaptchaSolver.solve(redir_url, sitekey, data_s);
                    } catch (JsonParserException | InterruptedException e) {
                        e.printStackTrace();
                    }

                    formBuilder.add("g-recaptcha-response", solved.getRecaptchaResponse());

                    var formReqBuilder = new okhttp3.Request.Builder()
                            .url("https://www.google.com/sorry/index")
                            .header("User-Agent", Constants.USER_AGENT)
                            .post(formBuilder.build());

                    var formResponse = Constants.h2_no_redir_client.newCall(formReqBuilder.build()).execute();

                    saved_cookie = HttpCookie.parse(URLUtils.silentDecode(StringUtils
                                    .substringAfter(formResponse.headers().get("Location"), "google_abuse=")))
                            .get(0);
                    cookie_received = System.currentTimeMillis();
                }

                if (saved_cookie != null) // call again as captcha has been solved or cookie has not expired.
                    execute(request);
            }

        }

        Map<String, List<String>> headerMap = resp.headers().entrySet().stream()
                .collect(Object2ObjectOpenHashMap::new, (m, e) -> m.put(e.getKey(), List.of(e.getValue())), Map::putAll);

        return new Response(resp.status(), null, headerMap, new String(resp.body()),
                resp.finalUrl());
    }
}
