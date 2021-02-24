package me.kavin.piped.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import me.kavin.piped.consts.Constants;

public class DownloaderImpl extends Downloader {

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
        request.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));

        builder.setHeader("User-Agent", Constants.USER_AGENT);

        HttpResponse<String> response = null;

        try {
            response = Constants.h2client.send(builder.build(), BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // ignored
        }

        // TODO: Implement solver
        if (response.statusCode() == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested", String.valueOf(response.uri()));
        }

        return new Response(response.statusCode(), "UNDEFINED", response.headers().map(), response.body(),
                String.valueOf(response.uri()));
    }
}
