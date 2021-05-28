package me.kavin.piped.utils;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import com.grack.nanojson.JsonParserException;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.SolvedCaptcha;

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
        Builder builder = HttpRequest.newBuilder(URI.create(request.url()));

        byte[] data = request.dataToSend();
        BodyPublisher publisher = data == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(data);

        builder.method(request.httpMethod(), publisher);

        builder.setHeader("User-Agent", Constants.USER_AGENT);

        if (saved_cookie != null && !saved_cookie.hasExpired())
            builder.setHeader("Cookie", saved_cookie.getName() + "=" + saved_cookie.getValue());

        request.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));

        HttpResponse<String> response = null;

        try {
            response = Constants.h2client.send(builder.build(), BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // ignored
        }

        if (response.statusCode() == 429) {

            synchronized (cookie_lock) {

                if (saved_cookie != null && saved_cookie.hasExpired()
                        || (System.currentTimeMillis() - cookie_received > TimeUnit.MINUTES.toMillis(30)))
                    saved_cookie = null;

                String redir_url = String.valueOf(response.request().uri());

                if (saved_cookie == null && redir_url.startsWith("https://www.google.com/sorry")) {

                    Map<String, String> formParams = new Object2ObjectOpenHashMap<>();
                    String sitekey = null, data_s = null;

                    for (Element el : Jsoup.parse(response.body()).selectFirst("form").children()) {
                        String name;
                        if (!(name = el.tagName()).equals("script")) {
                            if (name.equals("input"))
                                formParams.put(el.attr("name"), el.attr("value"));
                            else if (name.equals("div") && el.attr("id").equals("recaptcha")) {
                                sitekey = el.attr("data-sitekey");
                                data_s = el.attr("data-s");
                            }
                        }
                    }
                    if (sitekey == null || data_s == null)
                        throw new ReCaptchaException("Could not get recaptcha", redir_url);

                    SolvedCaptcha solved = null;

                    try {
                        solved = CaptchaSolver.solve(redir_url, sitekey, data_s);
                    } catch (JsonParserException | InterruptedException e) {
                        e.printStackTrace();
                    }

                    formParams.put("g-recaptcha-response", solved.getRecaptchaResponse());

                    Builder formBuilder = HttpRequest.newBuilder(URI.create("https://www.google.com/sorry/index"));

                    formBuilder.setHeader("User-Agent", Constants.USER_AGENT);

                    StringBuilder formBody = new StringBuilder();

                    formParams.forEach((name, value) -> {
                        formBody.append(name + "=" + URLUtils.silentEncode(value) + "&");
                    });

                    formBuilder.header("content-type", "application/x-www-form-urlencoded");

                    formBuilder.method("POST",
                            BodyPublishers.ofString(String.valueOf(formBody.substring(0, formBody.length() - 1))));

                    try {
                        HttpResponse<String> formResponse = Constants.h2_no_redir_client.send(formBuilder.build(),
                                BodyHandlers.ofString());

                        saved_cookie = HttpCookie.parse(URLUtils.silentDecode(StringUtils
                                .substringAfter(formResponse.headers().firstValue("Location").get(), "google_abuse=")))
                                .get(0);
                        cookie_received = System.currentTimeMillis();

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (saved_cookie != null) // call again as captcha has been solved or cookie has not expired.
                    execute(request);
            }

        }

        return new Response(response.statusCode(), "UNDEFINED", response.headers().map(), response.body(),
                String.valueOf(response.uri()));
    }
}
