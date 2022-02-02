package me.kavin.piped.utils;

import io.activej.http.*;
import io.activej.promise.Promisable;
import org.jetbrains.annotations.NotNull;

import static io.activej.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.activej.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;

public class CustomServletDecorator implements AsyncServlet {

    private static final HttpHeader HEADER = HttpHeaders.of("Server-Timing");

    private final AsyncServlet servlet;

    public CustomServletDecorator(AsyncServlet servlet) {
        this.servlet = servlet;
    }

    @Override
    public @NotNull Promisable<HttpResponse> serve(@NotNull HttpRequest request) throws Exception {
        long before = System.nanoTime();
        return servlet.serve(request).promise().map(response -> {

            HttpHeaderValue headerValue = HttpHeaderValue.of("app;dur=" + (System.nanoTime() - before) / 1000000.0);

            return response.withHeader(HEADER, headerValue).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .withHeader(ACCESS_CONTROL_ALLOW_HEADERS, "*, Authorization");

        });
    }
}
