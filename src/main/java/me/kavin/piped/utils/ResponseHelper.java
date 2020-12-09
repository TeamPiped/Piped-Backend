package me.kavin.piped.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.Channel;
import me.kavin.piped.utils.obj.ChannelPage;
import me.kavin.piped.utils.obj.PipedStream;
import me.kavin.piped.utils.obj.StreamItem;
import me.kavin.piped.utils.obj.Streams;
import me.kavin.piped.utils.obj.Subtitle;
import me.kavin.piped.utils.obj.search.SearchItem;
import me.kavin.piped.utils.obj.search.SearchStream;

public class ResponseHelper {

    public static final LoadingCache<String, CommentsInfo> commentsCache = Caffeine.newBuilder()
	    .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000)
	    .build(key -> CommentsInfo.getInfo("https://www.youtube.com/watch?v=" + key));

    public static final String streamsResponse(String videoId) throws Exception {

	CompletableFuture<StreamInfo> futureStream = CompletableFuture.supplyAsync(() -> {
	    try {
		return StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);
	    } catch (Exception e) {
		ExceptionUtils.rethrow(e);
	    }
	    return null;
	});

	CompletableFuture<String> futureLBRY = CompletableFuture.supplyAsync(() -> {
	    try {
		return getLBRYStreamURL(videoId);
	    } catch (Exception e) {
		ExceptionUtils.rethrow(e);
	    }
	    return null;
	});

	final List<Subtitle> subtitles = new ObjectArrayList<>();

	final StreamInfo info = futureStream.get();

	info.getSubtitles().forEach(subtitle -> subtitles
		.add(new Subtitle(rewriteURL(subtitle.getUrl()), subtitle.getFormat().getMimeType())));

	final List<PipedStream> videoStreams = new ObjectArrayList<>();
	final List<PipedStream> audioStreams = new ObjectArrayList<>();

	final String lbryURL = futureLBRY.get();

	if (lbryURL != null)
	    videoStreams.add(new PipedStream(lbryURL, "MP4", "LBRY", "video/mp4"));

	String hls = null;
	boolean livestream = false;

	if ((hls = info.getHlsUrl()) != null && !hls.isEmpty())
	    livestream = true;

	long minexpire = Long.MAX_VALUE;

	ObjectArrayList<Stream> allStreams = new ObjectArrayList<>();

	allStreams.addAll(info.getVideoStreams());
	allStreams.addAll(info.getAudioStreams());
	allStreams.addAll(info.getVideoOnlyStreams());

	for (Stream stream : allStreams) {

	    long expire = Long.parseLong(StringUtils.substringBetween(stream.getUrl(), "expire=", "&"));

	    if (expire < minexpire)
		minexpire = expire;

	}

	info.getVideoOnlyStreams().forEach(stream -> videoStreams.add(new PipedStream(rewriteURL(stream.getUrl()),
		String.valueOf(stream.getFormat()), stream.getResolution(), stream.getFormat().getMimeType())));
	info.getVideoStreams().forEach(stream -> videoStreams.add(new PipedStream(rewriteURL(stream.getUrl()),
		String.valueOf(stream.getFormat()), stream.getResolution(), stream.getFormat().getMimeType())));

	info.getAudioStreams()
		.forEach(stream -> audioStreams
			.add(new PipedStream(rewriteURL(stream.getUrl()), String.valueOf(stream.getFormat()),
				stream.getAverageBitrate() + " kbps", stream.getFormat().getMimeType())));

	final List<StreamItem> relatedStreams = new ObjectArrayList<>();

	info.getRelatedStreams().forEach(o -> {
	    StreamInfoItem item = (StreamInfoItem) o;
	    relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
		    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
		    item.getDuration(), item.getViewCount()));
	});

	final Streams streams = new Streams(info.getName(), info.getDescription().getContent(),
		info.getTextualUploadDate(), info.getUploaderName(), info.getUploaderUrl().substring(23),
		rewriteURL(info.getUploaderAvatarUrl()), rewriteURL(info.getThumbnailUrl()), info.getDuration(),
		info.getViewCount(), info.getLikeCount(), info.getDislikeCount(), audioStreams, videoStreams,
		relatedStreams, subtitles, livestream, hls);

	return Constants.mapper.writeValueAsString(streams);

    }

    public static final String channelResponse(String channelId)
	    throws IOException, ExtractionException, InterruptedException {

	final ChannelInfo info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);

	final List<StreamItem> relatedStreams = new ObjectArrayList<>();

	info.getRelatedItems().forEach(o -> {
	    StreamInfoItem item = o;
	    relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
		    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
		    item.getDuration(), item.getViewCount()));
	});

	String nextpage = info.hasNextPage() ? info.getNextPage().getUrl() : null;

	final Channel channel = new Channel(info.getName(), rewriteURL(info.getAvatarUrl()),
		rewriteURL(info.getBannerUrl()), info.getDescription(), nextpage, relatedStreams);

	return Constants.mapper.writeValueAsString(channel);

    }

    public static final String channelPageResponse(String channelId, String url)
	    throws IOException, ExtractionException, InterruptedException {

	InfoItemsPage<StreamInfoItem> page = ChannelInfo.getMoreItems(Constants.YOUTUBE_SERVICE,
		"https://youtube.com/channel/" + channelId, new Page(url));

	final List<StreamItem> relatedStreams = new ObjectArrayList<>();

	page.getItems().forEach(o -> {
	    StreamInfoItem item = o;
	    relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
		    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
		    item.getDuration(), item.getViewCount()));
	});

	String nextpage = page.hasNextPage() ? page.getNextPage().getUrl() : null;

	final ChannelPage channelpage = new ChannelPage(nextpage, relatedStreams);

	return Constants.mapper.writeValueAsString(channelpage);

    }

    final List<StreamItem> relatedStreams = new ObjectArrayList<>();

    public static final String trendingResponse() throws ParsingException, ExtractionException, IOException {

	final List<StreamItem> relatedStreams = new ObjectArrayList<>();

	String url = Constants.YOUTUBE_SERVICE.getKioskList().getListLinkHandlerFactoryByType("Trending")
		.getUrl("Trending");
	KioskInfo info = KioskInfo.getInfo(Constants.YOUTUBE_SERVICE, url);

	info.getRelatedItems().forEach(o -> {
	    StreamInfoItem item = o;
	    relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
		    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
		    item.getDuration(), item.getViewCount()));
	});

	return Constants.mapper.writeValueAsString(relatedStreams);
    }

    public static final String suggestionsResponse(String query)
	    throws JsonProcessingException, IOException, ExtractionException {

	return Constants.mapper
		.writeValueAsString(Constants.YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query));

    }

    public static final String searchResponse(String q) throws IOException, ExtractionException, InterruptedException {

	final SearchInfo info = SearchInfo.getInfo(Constants.YOUTUBE_SERVICE,
		Constants.YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q));

	ObjectArrayList<SearchItem> items = new ObjectArrayList<>();

	info.getRelatedItems().forEach(item -> {
	    switch (item.getInfoType()) {
	    case STREAM:
		StreamInfoItem stream = (StreamInfoItem) item;
		items.add(new SearchStream(item.getName(), rewriteURL(item.getThumbnailUrl()),
			item.getUrl().substring(23), stream.getViewCount(), stream.getDuration()));
		break;
	    case CHANNEL:
		items.add(new SearchItem(item.getName(), rewriteURL(item.getThumbnailUrl()),
			item.getUrl().substring(23)));
		break;
	    default:
		break;
	    }
	});

	return Constants.mapper.writeValueAsString(items);

    }

    private static final String getLBRYStreamURL(String videoId) throws IOException, InterruptedException {

	String lbryId = new JSONObject(Constants.h2client.send(HttpRequest
		.newBuilder(URI.create("https://api.lbry.com/yt/resolve?video_ids=" + URLUtils.silentEncode(videoId)))
		.setHeader("User-Agent", Constants.USER_AGENT).build(), BodyHandlers.ofString()).body())
			.getJSONObject("data").getJSONObject("videos").optString(videoId);

	if (!lbryId.isEmpty())
	    return rewriteURL(
		    new JSONObject(
			    Constants.h2client.send(
				    HttpRequest.newBuilder(URI.create("https://api.lbry.tv/api/v1/proxy?m=get"))
					    .POST(BodyPublishers.ofString(String.valueOf(new JSONObject()
						    .put("jsonrpc", "2.0").put("method", "get").put("params",
							    new JSONObject().put("uri", "lbry://" + lbryId)
								    .put("save_file", true)))))
					    .build(),
				    BodyHandlers.ofString()).body()).getJSONObject("result")
					    .getString("streaming_url"));

	return null;

    }

    private static String rewriteURL(final String old) {

	if (Constants.debug)
	    return old;

	if (old == null || old.isEmpty())
	    return null;

	URL url = null;

	try {
	    url = new URL(old);
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}

	final String host = url.getHost();

	String query = url.getQuery();

	boolean hasQuery = query != null;

	String path = url.getPath();

	path = path.replace("-rj", "-rw");

	if (path.startsWith("/vi/") && !path.contains("_live")) {
	    path = path.replace("/vi/", "/vi_webp/").replace(".jpg", ".webp").replace("hq720", "mqdefault")
		    .replace("hqdefault", "mqdefault");
	    hasQuery = false;
	}

	return Constants.PROXY_PART + path + (hasQuery ? "?" + query + "&host=" : "?host=")
		+ URLUtils.silentEncode(host);

    }
}
