package me.kavin.piped.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.activej.config.Config;
import io.activej.http.*;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.inject.module.Module;
import io.activej.launchers.http.MultithreadedHttpServerLauncher;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.server.handlers.*;
import me.kavin.piped.server.handlers.auth.AuthPlaylistHandlers;
import me.kavin.piped.server.handlers.auth.FeedHandlers;
import me.kavin.piped.server.handlers.auth.StorageHandlers;
import me.kavin.piped.server.handlers.auth.UserHandlers;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.resp.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Executor;

import static io.activej.config.converter.ConfigConverters.ofInetSocketAddress;
import static io.activej.http.HttpHeaders.*;
import static io.activej.http.HttpMethod.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.mapper;

public class ServerLauncher extends MultithreadedHttpServerLauncher {

    private static final HttpHeader FILE_NAME = HttpHeaders.of("x-file-name");
    private static final HttpHeader LAST_ETAG = HttpHeaders.of("x-last-etag");

    @Provides
    Executor executor() {
        return Multithreading.getCachedExecutor();
    }

    @Provides
    AsyncServlet mainServlet(Executor executor) {

        RoutingServlet router = RoutingServlet.create()
                .map(GET, "/healthcheck", AsyncServlet.ofBlocking(executor, request -> {
                    try (Session ignored = DatabaseSessionFactory.createSession()) {
                        return getRawResponse("OK".getBytes(UTF_8), "text/plain", "no-store");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/config", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(GenericHandlers.configResponse(), "public, max-age=86400");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                }))
                .map(GET, "/version", AsyncServlet.ofBlocking(executor, request -> getRawResponse(Constants.VERSION.getBytes(UTF_8), "text/plain", "no-store")))
                .map(HttpMethod.OPTIONS, "/*", request -> HttpResponse.ofCode(200))
                .map(GET, "/webhooks/pubsub", AsyncServlet.ofBlocking(executor, request -> {
                    var topic = request.getQueryParameter("hub.topic");
                    if (topic != null)
                        Multithreading.runAsyncLimited(() -> {
                            String channelId = StringUtils.substringAfter(topic, "channel_id=");
                            PubSubHelper.updatePubSub(channelId);
                        });

                    var challenge = request.getQueryParameter("hub.challenge");
                    return HttpResponse.ok200()
                            .withPlainText(Objects.requireNonNullElse(challenge, "ok"));
                })).map(POST, "/webhooks/pubsub", AsyncServlet.ofBlocking(executor, request -> {
                    try {

                        PubSubHandlers.handlePubSub(request.loadBody().getResult().asArray());

                        return HttpResponse.ofCode(204);

                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/sponsors/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                SponsorBlockUtils.getSponsors(request.getPathParameter("videoId"),
                                        request.getQueryParameter("category"), request.getQueryParameter("actionType")).getBytes(UTF_8),
                                "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/dearrow", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var videoIds = getArray(request.getQueryParameter("videoIds"));

                        return getJsonResponse(
                                SponsorBlockUtils.getDeArrowedInfo(videoIds)
                                        .thenApplyAsync(json -> {
                                            try {
                                                return mapper.writeValueAsBytes(json);
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }).get(),
                                "public, max-age=3600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/streams/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.streamsResponse(request.getPathParameter("videoId")),
                                "public, s-maxage=21540, max-age=30", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/clips/:clipId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.resolveClipId(request.getPathParameter("clipId")),
                                "public, max-age=31536000, immutable");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ChannelHandlers.channelResponse("channel/" + request.getPathParameter("channelId")),
                                "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/c/:name", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ChannelHandlers.channelResponse("c/" + request.getPathParameter("name")),
                                "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/user/:name", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ChannelHandlers.channelResponse("user/" + request.getPathParameter("name")),
                                "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/@/:handle", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                ChannelHandlers.channelResponse("@" + request.getPathParameter("handle")),
                                "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/channel/:channelId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(ChannelHandlers.channelPageResponse(request.getPathParameter("channelId"),
                                request.getQueryParameter("nextpage")), "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/channels/tabs", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        String nextpage = request.getQueryParameter("nextpage");
                        if (StringUtils.isEmpty(nextpage))
                            return getJsonResponse(ChannelHandlers.channelTabResponse(request.getQueryParameter("data")), "public, max-age=3600", true);
                        else
                            return getJsonResponse(ChannelHandlers.channelTabPageResponse(request.getQueryParameter("data"), nextpage), "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var playlistId = request.getPathParameter("playlistId");
                        var cache = StringUtils.isBlank(playlistId) || playlistId.length() != 36 ?
                                "public, max-age=600" : "private";
                        return getJsonResponse(me.kavin.piped.server.handlers.PlaylistHandlers.playlistResponse(playlistId), cache, true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                me.kavin.piped.server.handlers.PlaylistHandlers.playlistPageResponse(request.getPathParameter("playlistId"),
                                        request.getQueryParameter("nextpage")),
                                "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/rss/playlists/:playlistId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getRawResponse(
                                me.kavin.piped.server.handlers.PlaylistHandlers.playlistRSSResponse(request.getPathParameter("playlistId")),
                                "application/atom+xml", "public, s-maxage=600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                    // TODO: Replace with opensearch, below, for caching reasons.
                })).map(GET, "/suggestions", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(SearchHandlers.suggestionsResponse(request.getQueryParameter("query")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/opensearch/suggestions", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                SearchHandlers.opensearchSuggestionsResponse(request.getQueryParameter("query")),
                                "public, max-age=600");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/search", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(SearchHandlers.searchResponse(request.getQueryParameter("q"),
                                request.getQueryParameter("filter")), "public, max-age=600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/search", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(
                                SearchHandlers.searchPageResponse(request.getQueryParameter("q"),
                                        request.getQueryParameter("filter"), request.getQueryParameter("nextpage")),
                                "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/trending", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(TrendingHandlers.trendingResponse(request.getQueryParameter("region")),
                                "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.commentsResponse(request.getPathParameter("videoId")),
                                "public, max-age=1200", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/nextpage/comments/:videoId", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(StreamHandlers.commentsPageResponse(request.getPathParameter("videoId"),
                                request.getQueryParameter("nextpage")), "public, max-age=3600", true);
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/register", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        LoginRequest body = mapper.readValue(request.loadBody().getResult().asArray(),
                                LoginRequest.class);
                        return getJsonResponse(UserHandlers.registerResponse(body.username, body.password),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/login", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        LoginRequest body = mapper.readValue(request.loadBody().getResult().asArray(),
                                LoginRequest.class);
                        return getJsonResponse(UserHandlers.loginResponse(body.username, body.password), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/subscribe", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        SubscriptionUpdateRequest body = mapper
                                .readValue(request.loadBody().getResult().asArray(), SubscriptionUpdateRequest.class);
                        return getJsonResponse(
                                FeedHandlers.subscribeResponse(request.getHeader(AUTHORIZATION), body.channelId),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/unsubscribe", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        SubscriptionUpdateRequest body = mapper
                                .readValue(request.loadBody().getResult().asArray(), SubscriptionUpdateRequest.class);
                        return getJsonResponse(
                                FeedHandlers.unsubscribeResponse(request.getHeader(AUTHORIZATION), body.channelId),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/subscribed", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(FeedHandlers.isSubscribedResponse(request.getHeader(AUTHORIZATION),
                                request.getQueryParameter("channelId")), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/feed", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(FeedHandlers.feedResponse(request.getQueryParameter("authToken")),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/feed/rss", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getRawResponse(FeedHandlers.feedResponseRSS(request.getQueryParameter("authToken"),
                                        request.getQueryParameter("filter")),
                                "application/atom+xml", "public, s-maxage=120");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/feed/unauthenticated", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(FeedHandlers.unauthenticatedFeedResponse(
                                getArray(request.getQueryParameter("channels"))
                        ), "public, s-maxage=120");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/feed/unauthenticated", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        String[] subscriptions = mapper.readValue(request.loadBody().getResult().asArray(),
                                String[].class);
                        return getJsonResponse(FeedHandlers.unauthenticatedFeedResponse(subscriptions), "public, s-maxage=120");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/feed/unauthenticated/rss", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getRawResponse(FeedHandlers.unauthenticatedFeedResponseRSS(
                                getArray(request.getQueryParameter("channels")),
                                request.getQueryParameter("filter")
                        ), "application/atom+xml", "public, s-maxage=120");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/import", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        String[] subscriptions = mapper.readValue(request.loadBody().getResult().asArray(),
                                String[].class);
                        return getJsonResponse(FeedHandlers.importResponse(request.getHeader(AUTHORIZATION),
                                subscriptions, Boolean.parseBoolean(request.getQueryParameter("override"))), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/import/playlist", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var json = mapper.readTree(request.loadBody().getResult().asArray());
                        var playlistId = json.get("playlistId").textValue();
                        return getJsonResponse(AuthPlaylistHandlers.importPlaylistResponse(request.getHeader(AUTHORIZATION), playlistId), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/subscriptions", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(FeedHandlers.subscriptionsResponse(request.getHeader(AUTHORIZATION)),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/subscriptions/unauthenticated", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(FeedHandlers.unauthenticatedSubscriptionsResponse(
                                Objects.requireNonNull(request.getQueryParameter("channels")).split(",")
                        ), "public, s-maxage=120");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/subscriptions/unauthenticated", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        String[] subscriptions = mapper.readValue(request.loadBody().getResult().asArray(),
                                String[].class);
                        return getJsonResponse(FeedHandlers.unauthenticatedSubscriptionsResponse(subscriptions), "public, s-maxage=120");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/user/playlists/create", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var name = mapper.readTree(request.loadBody().getResult().asArray()).get("name").textValue();
                        return getJsonResponse(AuthPlaylistHandlers.createPlaylist(request.getHeader(AUTHORIZATION), name), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(PATCH, "/user/playlists/description", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var json = mapper.readTree(request.loadBody().getResult().asArray());
                        var playlistId = json.get("playlistId").textValue();
                        var description = json.get("description").textValue();
                        return getJsonResponse(
                                AuthPlaylistHandlers.editPlaylistDescriptionResponse(request.getHeader(AUTHORIZATION),
                                        playlistId, description),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/user/playlists", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(AuthPlaylistHandlers.playlistsResponse(request.getHeader(AUTHORIZATION)), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/user/playlists/add", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var json = mapper.readTree(request.loadBody().getResult().asArray());
                        var playlistId = json.get("playlistId").textValue();
                        var videoIds = new ObjectArrayList<String>();
                        // backwards compatibility
                        var videoIdField = json.get("videoId");
                        if (videoIdField != null) {
                            videoIds.add(videoIdField.textValue());
                        }
                        var videoIdsField = json.get("videoIds");
                        if (videoIdsField != null) {
                            for (JsonNode node : videoIdsField) {
                                videoIds.add(node.textValue());
                            }
                        }

                        return getJsonResponse(AuthPlaylistHandlers.addToPlaylistResponse(request.getHeader(AUTHORIZATION), playlistId, videoIds), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/user/playlists/remove", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var json = mapper.readTree(request.loadBody().getResult().asArray());
                        var playlistId = json.get("playlistId").textValue();
                        var index = json.get("index").intValue();
                        return getJsonResponse(AuthPlaylistHandlers.removeFromPlaylistResponse(request.getHeader(AUTHORIZATION), playlistId, index), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/user/playlists/clear", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var json = mapper.readTree(request.loadBody().getResult().asArray());
                        var playlistId = json.get("playlistId").textValue();
                        return getJsonResponse(AuthPlaylistHandlers.clearPlaylistResponse(request.getHeader(AUTHORIZATION), playlistId), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/user/playlists/rename", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var json = mapper.readTree(request.loadBody().getResult().asArray());
                        var playlistId = json.get("playlistId").textValue();
                        var newName = json.get("newName").textValue();
                        return getJsonResponse(AuthPlaylistHandlers.renamePlaylistResponse(request.getHeader(AUTHORIZATION), playlistId, newName), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/user/playlists/delete", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var json = mapper.readTree(request.loadBody().getResult().asArray());
                        var playlistId = json.get("playlistId").textValue();
                        return getJsonResponse(AuthPlaylistHandlers.deletePlaylistResponse(request.getHeader(AUTHORIZATION), playlistId), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/registered/badge", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return HttpResponse.ofCode(302).withHeader(LOCATION, GenericHandlers.registeredBadgeRedirect())
                                .withHeader(CACHE_CONTROL, "public, max-age=30");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/user/delete", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        DeleteUserRequest body = mapper.readValue(request.loadBody().getResult().asArray(),
                                DeleteUserRequest.class);
                        return getJsonResponse(UserHandlers.deleteUserResponse(request.getHeader(AUTHORIZATION), body.password),
                                "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/logout", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        return getJsonResponse(UserHandlers.logoutResponse(request.getHeader(AUTHORIZATION)), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/storage/stat", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var file = request.getQueryParameter("file");
                        return getJsonResponse(StorageHandlers.statFile(request.getHeader(AUTHORIZATION), file), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(POST, "/storage/put", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var data = request.loadBody().getResult().asArray();

                        String fileName = request.getHeader(FILE_NAME);
                        String etag = request.getHeader(LAST_ETAG);

                        return getJsonResponse(StorageHandlers.putFile(request.getHeader(AUTHORIZATION), fileName, etag, data), "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                })).map(GET, "/storage/get", AsyncServlet.ofBlocking(executor, request -> {
                    try {
                        var file = request.getQueryParameter("file");
                        return getRawResponse(StorageHandlers.getFile(request.getHeader(AUTHORIZATION), file), "application/octet-stream", "private");
                    } catch (Exception e) {
                        return getErrorResponse(e, request.getPath());
                    }
                }))
                .map(GET, "/", AsyncServlet.ofBlocking(executor, request -> HttpResponse.redirect302(Constants.FRONTEND_URL)));

        return new CustomServletDecorator(router);
    }

    private static String[] getArray(String s) {

        if (s == null) {
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse());
        }

        return s.split(",");
    }

    @Override
    protected Module getOverrideModule() {
        return new AbstractModule() {
            @Provides
            Config config() {
                return Config.create()
                        .with("http.listenAddresses",
                                Config.ofValue(ofInetSocketAddress(), new InetSocketAddress(Constants.PORT)))
                        .with("bytebuf.useWatchdog", String.valueOf(true))
                        .with("workers", Constants.HTTP_WORKERS);
            }
        };
    }

    private @NotNull HttpResponse getJsonResponse(byte[] body, String cache) {
        return getJsonResponse(200, body, cache, false);
    }

    private @NotNull HttpResponse getJsonResponse(byte[] body, String cache, boolean prefetchProxy) {
        return getJsonResponse(200, body, cache, prefetchProxy);
    }

    private @NotNull HttpResponse getJsonResponse(int code, byte[] body, String cache) {
        return getJsonResponse(code, body, cache, false);
    }

    private @NotNull HttpResponse getJsonResponse(int code, byte[] body, String cache, boolean prefetchProxy) {
        return getRawResponse(code, body, "application/json", cache, prefetchProxy);
    }

    private @NotNull HttpResponse getRawResponse(byte[] body, String contentType, String cache) {
        return getRawResponse(200, body, contentType, cache, false);
    }

    private @NotNull HttpResponse getRawResponse(int code, byte[] body, String contentType, String cache,
                                                 boolean prefetchProxy) {
        HttpResponse response = HttpResponse.ofCode(code).withBody(body).withHeader(CONTENT_TYPE, contentType)
                .withHeader(CACHE_CONTROL, cache);
        if (prefetchProxy)
            response = response.withHeader(LINK, String.format("<%s>; rel=preconnect", Constants.IMAGE_PROXY_PART));
        return response;
    }

    private @NotNull HttpResponse getErrorResponse(Exception e, String path) {

        e = ExceptionHandler.handle(e, path);

        if (e instanceof ErrorResponse error) {
            return getJsonResponse(error.getCode(), error.getContent(), "private");
        }

        try {
            return getJsonResponse(500, mapper
                    .writeValueAsBytes(new StackTraceResponse(ExceptionUtils.getStackTrace(e), e.getMessage())), "private");
        } catch (JsonProcessingException ex) {
            return HttpResponse.ofCode(500);
        }
    }
}
