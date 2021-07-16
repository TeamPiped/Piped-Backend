package me.kavin.piped.utils;

import static io.activej.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.activej.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;

import org.jetbrains.annotations.NotNull;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promisable;

public class CustomServletDecorator implements AsyncServlet {

    private static final HttpHeader HEADER = HttpHeaders.of("Server-Timing");

    private final AsyncServlet servlet;

    public CustomServletDecorator(AsyncServlet servlet) {
        this.servlet = servlet;
    }

    @Override
    public @NotNull Promisable<HttpResponse> serve(@NotNull HttpRequest request) {
        long before = System.nanoTime();
        return servlet.serve(request).promise().map(response -> {

            HttpHeaderValue headerValue = HttpHeaderValue.of("app;dur=" + (System.nanoTime() - before) / 1000000.0);

            return response.withHeader(HEADER, headerValue).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .withHeader(ACCESS_CONTROL_ALLOW_HEADERS, "*");

        });
    }
}
