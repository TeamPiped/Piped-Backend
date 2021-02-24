package me.kavin.piped;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DownloaderImpl;
import me.kavin.piped.utils.ResponseHelper;
import me.kavin.piped.utils.SponsorBlockUtils;
import reactor.core.publisher.Flux;
import reactor.netty.ByteBufFlux;
import reactor.netty.DisposableServer;
import reactor.netty.NettyOutbound;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;

public class Main {

    public static void main(String[] args) throws Exception {

        NewPipe.init(new DownloaderImpl(), new Localization("en", "US"));

        DisposableServer server = HttpServer.create().port(Constants.PORT).route(routes -> {

            routes.get("/webhooks/pubsub", (req, res) -> {

                long start = System.nanoTime();
                QueryStringDecoder query = new QueryStringDecoder(req.uri());

                try {
                    return writeResponse(res, query.parameters().get("hub.challenge").get(0), TEXT_PLAIN, 200,
                            "private", start);

                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.post("/webhooks/pubsub", (req, res) -> {

                long start = System.nanoTime();

                try {
                    req.receive().asInputStream().subscribe(in -> {
                        try {
                            SyndFeed feed = new SyndFeedInput().build(new XmlReader(in));

                            feed.getEntries().forEach(entry -> {
                                System.out.println(entry.getLinks().get(0).getHref());
                                System.out.println(entry.getAuthors().get(0).getUri());
                            });

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return writeResponse(res, "ok", TEXT_PLAIN, 200, "private", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/sponsors/{videoId}", (req, res) -> {

                long start = System.nanoTime();
                QueryStringDecoder query = new QueryStringDecoder(req.uri());

                try {
                    return writeResponse(res, SponsorBlockUtils.getSponsors(req.param("videoId"),
                            query.parameters().get("category").get(0)), 200, "public, max-age=3600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/streams/{videoId}", (req, res) -> {

                long start = System.nanoTime();
                try {
                    // The stream links are valid for 6 hours.
                    return writeResponse(res, ResponseHelper.streamsResponse(req.param("videoId")), 200,
                            "public, s-maxage=21540", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/channels/{channelId}", (req, res) -> {

                long start = System.nanoTime();
                try {
                    return writeResponse(res, ResponseHelper.channelResponse(req.param("channelId")), 200,
                            "public, max-age=600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/nextpage/channels/{channelId}", (req, res) -> {

                long start = System.nanoTime();
                QueryStringDecoder query = new QueryStringDecoder(req.uri());

                try {
                    return writeResponse(res, ResponseHelper.channelPageResponse(req.param("channelId"),
                            query.parameters().get("url").get(0)), 200, "public, max-age=3600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/playlists/{playlistId}", (req, res) -> {

                long start = System.nanoTime();
                try {
                    return writeResponse(res, ResponseHelper.playlistResponse(req.param("playlistId")), 200,
                            "public, max-age=600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/nextpage/playlists/{playlistId}", (req, res) -> {

                long start = System.nanoTime();
                QueryStringDecoder query = new QueryStringDecoder(req.uri());

                try {
                    return writeResponse(res, ResponseHelper.playlistPageResponse(req.param("playlistId"),
                            query.parameters().get("url").get(0)), 200, "public, max-age=3600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/suggestions", (req, res) -> {

                long start = System.nanoTime();
                QueryStringDecoder query = new QueryStringDecoder(req.uri());

                try {
                    return writeResponse(res,
                            ResponseHelper.suggestionsResponse(query.parameters().get("query").get(0)), 200,
                            "public, max-age=600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/search", (req, res) -> {

                long start = System.nanoTime();
                QueryStringDecoder query = new QueryStringDecoder(req.uri());

                try {
                    return writeResponse(res, ResponseHelper.searchResponse(query.parameters().get("q").get(0)), 200,
                            "public, max-age=600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/nextpage/search", (req, res) -> {

                long start = System.nanoTime();
                QueryStringDecoder query = new QueryStringDecoder(req.uri());

                try {
                    return writeResponse(res,
                            ResponseHelper.searchPageResponse(query.parameters().get("q").get(0),
                                    query.parameters().get("url").get(0), query.parameters().get("id").get(0)),
                            200, "public, max-age=3600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

            routes.get("/trending", (req, res) -> {

                long start = System.nanoTime();
                try {
                    return writeResponse(res, ResponseHelper.trendingResponse(), 200, "public, max-age=3600", start);
                } catch (Exception e) {
                    e.printStackTrace();
                    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private", start);
                }

            });

        }).compress(true).bindNow();

        server.onDispose().block();

    }

    public static NettyOutbound writeResponse(HttpServerResponse res, String resp, int code, String cache, long time) {
        return writeResponse(res, resp, APPLICATION_JSON, code, cache, time);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, String resp, AsciiString mimeType, int code,
            String cache, long time) {
        return writeResponse(res, resp, mimeType.toString(), code, cache, time);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, String resp, String mimeType, int code,
            String cache, long time) {
        return writeResponse(res, resp.getBytes(StandardCharsets.UTF_8), mimeType, code, cache, time);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, byte[] resp, int code, String cache, long time) {
        return writeResponse(res, resp, APPLICATION_JSON, code, cache, time);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, byte[] resp, AsciiString mimeType, int code,
            String cache, long time) {
        return writeResponse(res, resp, mimeType.toString(), code, cache, time);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, byte[] resp, String mimeType, int code,
            String cache, long time) {
        return res.status(code).addHeader(CONTENT_TYPE, mimeType).addHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .addHeader(CACHE_CONTROL, cache)
                .addHeader("Server-Timing", "app;dur=" + (System.nanoTime() - time) / 1000000.0)
                .sendByteArray(Flux.just(resp));
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, Flux<String> resp, int code, String cache) {
        return writeResponse(res, resp, APPLICATION_JSON, code, cache);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, Flux<String> resp, AsciiString mimeType, int code,
            String cache) {
        return writeResponse(res, resp, mimeType.toString(), code, cache);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, Flux<String> resp, String mimeType, int code,
            String cache) {
        return res.status(code).addHeader(CONTENT_TYPE, mimeType).addHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .addHeader(CACHE_CONTROL, cache)
                .send(ByteBufFlux.fromString(resp, java.nio.charset.StandardCharsets.UTF_8, ByteBufAllocator.DEFAULT));
    }
}
