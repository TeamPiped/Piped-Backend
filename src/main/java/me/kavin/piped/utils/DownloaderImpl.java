package me.kavin.piped.utils;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.grack.nanojson.JsonParserException;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.SolvedCaptcha;
import okhttp3.FormBody;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.concurrent.TimeUnit;

public class DownloaderImpl extends Downloader {

    private static HttpCookie saved_cookie;
    private static long cookie_received;
    private static final Object cookie_lock = new Object();

    final AsyncLoadingCache<Request, Response> responseCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .scheduler(Scheduler.systemScheduler())
            .executor(Multithreading.getCachedExecutor())
            .maximumSize(100).buildAsync(this::executeRequest);

    @Override
    public Response execute(@NotNull Request request) {
        try {
            return responseCache.get(request).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes a request with HTTP/2.
     */
    public Response executeRequest(Request request) throws IOException, ReCaptchaException {

        // TODO: HTTP/3 aka QUIC
        var bytes = request.dataToSend();
        RequestBody body = null;
        if (bytes != null)
            body = RequestBody.create(bytes);

        var builder = new okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), body)
                .header("User-Agent", Constants.USER_AGENT);

        if (saved_cookie != null && !saved_cookie.hasExpired())
            builder.header("Cookie", saved_cookie.getName() + "=" + saved_cookie.getValue());

        request.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));

        var resp = Constants.h2client.newCall(builder.build()).execute();

        if (resp.code() == 429) {

            synchronized (cookie_lock) {

                if (saved_cookie != null && saved_cookie.hasExpired()
                        || (System.currentTimeMillis() - cookie_received > TimeUnit.MINUTES.toMillis(30)))
                    saved_cookie = null;

                String redir_url = String.valueOf(resp.request().url());

                if (saved_cookie == null && redir_url.startsWith("https://www.google.com/sorry")) {

                    var formBuilder = new FormBody.Builder();
                    String sitekey = null, data_s = null;

                    for (Element el : Jsoup.parse(resp.body().string()).selectFirst("form").children()) {
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

        var response = new Response(resp.code(), resp.message(), resp.headers().toMultimap(), resp.body().string(),
                String.valueOf(resp.request().url()));

        resp.close();

        return response;
    }
}
