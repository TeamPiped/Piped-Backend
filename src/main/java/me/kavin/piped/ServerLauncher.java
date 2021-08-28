package me.kavin.piped;

import static io.activej.config.converter.ConfigConverters.ofInetSocketAddress;
import static io.activej.http.HttpHeaders.AUTHORIZATION;
import static io.activej.http.HttpHeaders.CACHE_CONTROL;
import static io.activej.http.HttpHeaders.CONTENT_TYPE;
import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
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
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.Multithreading;
import me.kavin.piped.utils.ResponseHelper;
import me.kavin.piped.utils.SponsorBlockUtils;
import me.kavin.piped.utils.resp.ErrorResponse;
import me.kavin.piped.utils.resp.LoginRequest;
import me.kavin.piped.utils.resp.SubscriptionUpdateRequest;

public class ServerLauncher extends MultithreadedHttpServerLauncher {

    @Provides
    Executor executor() {
        return Multithreading.getCachedExecutor();
    }

    @Provides
    AsyncServlet mainServlet(Executor executor) {

        RoutingServlet router = RoutingServlet.create()
                .map(HttpMethod.OPTIONS, "/*", request -> HttpResponse.ofCode(200))
                .map(GET, "/webhooks/pubsub", request -> {
                    return HttpResponse.ok200().withPlainText(request.getQueryParameter("hub.challenge"));
                }).map(POST, "/webhooks/pubsub", AsyncServlet.ofBlocking(executor, request -> {
                    try {

                        SyndFeed feed = new SyndFeedInput().build(
                                new InputSource(new ByteArrayInputStream(request.loadBody().getResult().asArray())));

                        Multithreading.runAsync(() -> {
                            Session s = DatabaseSessionFactory.createSession();
                            feed.getEntries().forEach(entry -> {
                                System.out.println(entry.getLinks().get(0).getHref());
                                ResponseHelper.handleNewVideo(entry.getLinks().get(0).getHref(),
                                        entry.getPublishedDate().getTime(), null, s);
                            });
                            s.close();
                        });

                        return HttpResponse.ofCode(204);

                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/sponsors/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                SponsorBlockUtils.getSponsors(request.getPathParameter("videoId"),
                                        request.getQueryParameter("category")).getBytes(StandardCharsets.UTF_8),
                                "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/streams/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.streamsResponse(request.getPathParameter("videoId")),
                                "public, s-maxage=21540");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ResponseHelper.channelResponse("channel/" + request.getPathParameter("channelId")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/c/:name", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.channelResponse("c/" + request.getPathParameter("name")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/user/:name", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ResponseHelper.channelResponse("user/" + request.getPathParameter("name")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/nextpage/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.channelPageResponse(request.getPathParameter("channelId"),
                                request.getQueryParameter("nextpage")), "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.playlistResponse(request.getPathParameter("playlistId")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/nextpage/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ResponseHelper.playlistPageResponse(request.getPathParameter("playlistId"),
                                        request.getQueryParameter("nextpage")),
                                "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/rss/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ResponseHelper.playlistRSSResponse(request.getPathParameter("playlistId")),
                                "public, s-maxage=600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/suggestions", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.suggestionsResponse(request.getQueryParameter("query")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/search", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.searchResponse(request.getQueryParameter("q"),
                                request.getQueryParameter("filter")), "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/nextpage/search", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ResponseHelper.searchPageResponse(request.getQueryParameter("q"),
                                        request.getQueryParameter("filter"), request.getQueryParameter("nextpage")),
                                "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/trending", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.trendingResponse(request.getQueryParameter("region")),
                                "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.commentsResponse(request.getPathParameter("videoId")),
                                "public, max-age=1200");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/nextpage/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.commentsPageResponse(request.getPathParameter("videoId"),
                                request.getQueryParameter("nextpage")), "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(POST, "/register", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        LoginRequest body = Constants.mapper.readValue(request.loadBody().getResult().asArray(),
                                LoginRequest.class);
                        return getJsonResponse(ResponseHelper.registerResponse(body.username, body.password),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(POST, "/login", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        LoginRequest body = Constants.mapper.readValue(request.loadBody().getResult().asArray(),
                                LoginRequest.class);
                        return getJsonResponse(ResponseHelper.loginResponse(body.username, body.password), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(POST, "/subscribe", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        SubscriptionUpdateRequest body = Constants.mapper
                                .readValue(request.loadBody().getResult().asArray(), SubscriptionUpdateRequest.class);
                        return getJsonResponse(
                                ResponseHelper.subscribeResponse(request.getHeader(AUTHORIZATION), body.channelId),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(POST, "/unsubscribe", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        SubscriptionUpdateRequest body = Constants.mapper
                                .readValue(request.loadBody().getResult().asArray(), SubscriptionUpdateRequest.class);
                        return getJsonResponse(
                                ResponseHelper.unsubscribeResponse(request.getHeader(AUTHORIZATION), body.channelId),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/subscribed", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.isSubscribedResponse(request.getHeader(AUTHORIZATION),
                                request.getQueryParameter("channelId")), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/feed", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.feedResponse(request.getQueryParameter("authToken")),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/feed/rss", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.feedResponseRSS(request.getQueryParameter("authToken")),
                                "public, s-maxage=120");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(POST, "/import", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        String[] subscriptions = Constants.mapper.readValue(request.loadBody().getResult().asArray(),
                                String[].class);
                        return getJsonResponse(ResponseHelper.importResponse(request.getHeader(AUTHORIZATION),
                                subscriptions, Boolean.parseBoolean(request.getQueryParameter("override"))), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e);
                    }
                })).map(GET, "/subscriptions", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ResponseHelper.subscriptionsResponse(request.getHeader(AUTHORIZATION)),
                                "private");
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
                                Config.ofValue(ofInetSocketAddress(), new InetSocketAddress(Constants.PORT)))
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

        if (e.getCause() != null && e instanceof ExecutionException)
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
