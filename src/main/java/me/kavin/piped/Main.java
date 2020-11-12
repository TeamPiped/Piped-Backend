package me.kavin.piped;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;

import io.netty.handler.codec.http.QueryStringDecoder;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DownloaderImpl;
import me.kavin.piped.utils.ResponseHelper;
import me.kavin.piped.utils.SponsorBlockUtils;
import reactor.core.publisher.Flux;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.server.HttpServer;

public class Main {

    public static void main(String[] args) throws Exception {

	System.setProperty("file.encoding", "UTF-8");

//	SyndFeed feed = new SyndFeedInput().build(new XmlReader(new FileInputStream("pubsub.xml")));
//
//	feed.getEntries().forEach(entry -> {
//	    System.out.println(entry.getLinks().get(0).getHref());
//	    System.out.println(entry.getAuthors().get(0).getUri());
//	});

	NewPipe.init(new DownloaderImpl(), new Localization("en", "US"));

	HttpServer.create().port(Constants.PORT).route(routes -> {

	    routes.get("/webhooks/pubsub", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return res.compression(true).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just(query.parameters().get("hub.challenge").get(0))));
		} catch (Exception e) {
		    e.printStackTrace();
		    return res.compression(true).status(500).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just(ExceptionUtils.getStackTrace(e))));
		}

	    });

	    routes.post("/webhooks/pubsub", (req, res) -> {

		try {
		    req.receive().asString().subscribe(str -> System.out.println(str));
		    return res.compression(true).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just("ok")));
		} catch (Exception e) {
		    e.printStackTrace();
		    return res.compression(true).status(500).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just(ExceptionUtils.getStackTrace(e))));
		}

	    });

	    routes.get("/sponsors/{videoId}", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return res.compression(true).addHeader("Access-Control-Allow-Origin", "*")
			    .addHeader("Cache-Control", "public, s-maxage=3600")
			    .send(ByteBufFlux.fromString(Flux.just(SponsorBlockUtils.getSponsors(req.param("videoId"),
				    query.parameters().get("category").get(0)))));
		} catch (Exception e) {
		    e.printStackTrace();
		    return res.compression(true).status(500).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just(ExceptionUtils.getStackTrace(e))));
		}

	    });

	    routes.get("/streams/{videoId}", (req, res) -> {

		try {
		    // The stream links are valid for 6 hours.
		    return res.compression(true).addHeader("Access-Control-Allow-Origin", "*")
			    .addHeader("Cache-Control", "public, s-maxage=21540").send(ByteBufFlux
				    .fromString(Flux.just(ResponseHelper.streamsResponse(req.param("videoId")))));
		} catch (Exception e) {
		    e.printStackTrace();
		    return res.compression(true).status(500).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just(ExceptionUtils.getStackTrace(e))));
		}

	    });

	    routes.get("/channels/{channelId}", (req, res) -> {

		try {
		    // The stream links are valid for 6 hours.
		    return res.compression(true).addHeader("Access-Control-Allow-Origin", "*")
			    .addHeader("Cache-Control", "public, s-maxage=21540").send(ByteBufFlux
				    .fromString(Flux.just(ResponseHelper.channelResponse(req.param("channelId")))));
		} catch (Exception e) {
		    e.printStackTrace();
		    return res.compression(true).status(500).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just(ExceptionUtils.getStackTrace(e))));
		}

	    });

	    routes.get("/trending", (req, res) -> {

		try {
		    // The stream links are valid for 6 hours.
		    return res.compression(true).addHeader("Access-Control-Allow-Origin", "*")
			    .addHeader("Cache-Control", "public, s-maxage=3600")
			    .send(ByteBufFlux.fromString(Flux.just(ResponseHelper.trendingResponse())));
		} catch (Exception e) {
		    e.printStackTrace();
		    return res.compression(true).status(500).addHeader("Cache-Control", "private")
			    .send(ByteBufFlux.fromString(Flux.just(ExceptionUtils.getStackTrace(e))));
		}

	    });

	}).bindNow();

	Thread.sleep(Long.MAX_VALUE);
    }
}
