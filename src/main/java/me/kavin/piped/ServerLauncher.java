package me.kavin.piped;

import static io.activej.config.converter.ConfigConverters.ofInetSocketAddress;
import static io.activej.http.HttpHeaders.CACHE_CONTROL;
import static io.activej.http.HttpHeaders.CONTENT_TYPE;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;

import io.activej.config.Config;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.inject.module.Module;
import io.activej.launchers.http.MultithreadedHttpServerLauncher;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.CustomServletDecorator;
import me.kavin.piped.utils.ResponseHelper;
import me.kavin.piped.utils.SponsorBlockUtils;
import me.kavin.piped.utils.resp.ErrorResponse;

public class ServerLauncher extends MultithreadedHttpServerLauncher {

    @Provides
    Executor executor() {
        return Executors.newCachedThreadPool();
    }

    @Provides
    AsyncServlet mainServlet(Executor executor) {

        RoutingServlet router = RoutingServlet.create().map(HttpMethod.GET, "/webhooks/pubsub", request -> {
            return HttpResponse.ok200().withPlainText(request.getQueryParameter("hub.challenge"));
        }).map(HttpMethod.POST, "/webhooks/pubsub", AsyncServlet.ofBlocking(executor, request -> {
            try {

                SyndFeed feed = new SyndFeedInput()
                        .build(new InputSource(new ByteArrayInputStream(request.loadBody().getResult().asArray())));

                feed.getEntries().forEach(entry -> {
                    System.out.println(entry.getLinks().get(0).getHref());
                    System.out.println(entry.getAuthors().get(0).getUri());
                });

                return HttpResponse.ofCode(204);

            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/sponsors/:videoId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(SponsorBlockUtils
                        .getSponsors(request.getPathParameter("videoId"), request.getQueryParameter("category"))
                        .getBytes(StandardCharsets.UTF_8), "public, max-age=3600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/streams/:videoId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.streamsResponse(request.getPathParameter("videoId")),
                        "public, s-maxage=21540");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(
                        ResponseHelper.channelResponse("channel/" + request.getPathParameter("channelId")),
                        "public, max-age=600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/c/:name", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.channelResponse("c/" + request.getPathParameter("name")),
                        "public, max-age=600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/user/:name", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.channelResponse("user/" + request.getPathParameter("name")),
                        "public, max-age=600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/nextpage/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.channelPageResponse(request.getPathParameter("channelId"),
                        request.getQueryParameter("nextpage")), "public, max-age=3600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.playlistResponse(request.getPathParameter("playlistId")),
                        "public, max-age=600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/nextpage/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.playlistPageResponse(request.getPathParameter("playlistId"),
                        request.getQueryParameter("nextpage")), "public, max-age=3600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/suggestions", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.suggestionsResponse(request.getQueryParameter("query")),
                        "public, max-age=600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/search", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.searchResponse(request.getQueryParameter("q"),
                        request.getQueryParameter("filter")), "public, max-age=600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/nextpage/search", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(
                        ResponseHelper.searchPageResponse(request.getQueryParameter("q"),
                                request.getQueryParameter("filter"), request.getQueryParameter("nextpage")),
                        "public, max-age=3600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/trending", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.trendingResponse(request.getQueryParameter("region")),
                        "public, max-age=3600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.commentsResponse(request.getPathParameter("videoId")),
                        "public, max-age=1200");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        })).map("/nextpage/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
            try {
                return getJsonResponse(ResponseHelper.commentsPageResponse(request.getPathParameter("videoId"),
                        request.getQueryParameter("url")), "public, max-age=3600");
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }));

        return new CustomServletDecorator(router);
    }

    @Override
    protected Module getOverrideModule() {
        return new AbstractModule() {
            @Provides
            Config config() {
                return Config.create()
                        .with("http.listenAddresses",
                                Config.ofValue(ofInetSocketAddress(), new InetSocketAddress(PORT)))
                        .with("workers", Constants.HTTP_WORKERS);
            }
        };
    }

    private @NotNull HttpResponse getJsonResponse(byte[] body, String cache) {
        return getJsonResponse(200, body, cache);
    }

    private @NotNull HttpResponse getJsonResponse(int code, byte[] body, String cache) {
        return HttpResponse.ofCode(code).withBody(body).withHeader(CONTENT_TYPE, "application/json")
                .withHeader(CACHE_CONTROL, cache);
    }

    private @NotNull HttpResponse getErrorResponse(Exception e) {

        if (e instanceof ExecutionException)
            e = (Exception) e.getCause();

        if (!(e instanceof AgeRestrictedContentException || e instanceof ContentNotAvailableException))
            e.printStackTrace();

        try {
            return getJsonResponse(500, Constants.mapper
                    .writeValueAsBytes(new ErrorResponse(ExceptionUtils.getStackTrace(e), e.getMessage())), "private");
        } catch (JsonProcessingException ex) {
            return HttpResponse.ofCode(500);
        }
    }
}
