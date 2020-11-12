package me.kavin.piped.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.Channel;
import me.kavin.piped.utils.obj.Stream;
import me.kavin.piped.utils.obj.StreamItem;
import me.kavin.piped.utils.obj.Streams;
import me.kavin.piped.utils.obj.Subtitle;

public class ResponseHelper {

    public static final LoadingCache<String, CommentsInfo> commentsCache = Caffeine.newBuilder()
	    .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000)
	    .build(key -> CommentsInfo.getInfo("https://www.youtube.com/watch?v=" + key));

    public static final String streamsResponse(String videoId)
	    throws IOException, ExtractionException, InterruptedException {

	final StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);

	final List<Subtitle> subtitles = new ObjectArrayList<>();

	info.getSubtitles().forEach(subtitle -> subtitles
		.add(new Subtitle(rewriteURL(subtitle.getUrl()), subtitle.getFormat().getMimeType())));

	final List<Stream> videoStreams = new ObjectArrayList<>();
	final List<Stream> audioStreams = new ObjectArrayList<>();

	String lbryURL = getLBRYStreamURL(videoId);

	if (lbryURL != null)
	    videoStreams.add(new Stream(lbryURL, "MP4", "LBRY", "video/mp4"));

	info.getVideoOnlyStreams().forEach(stream -> videoStreams.add(new Stream(rewriteURL(stream.getUrl()),
		String.valueOf(stream.getFormat()), stream.getResolution(), stream.getFormat().getMimeType())));
	info.getVideoStreams().forEach(stream -> videoStreams.add(new Stream(rewriteURL(stream.getUrl()),
		String.valueOf(stream.getFormat()), stream.getResolution(), stream.getFormat().getMimeType())));

	info.getAudioStreams().forEach(
		stream -> audioStreams.add(new Stream(rewriteURL(stream.getUrl()), String.valueOf(stream.getFormat()),
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
		relatedStreams, subtitles);

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

	final Channel channel = new Channel(info.getName(), info.getAvatarUrl(), info.getBannerUrl(),
		info.getDescription(), relatedStreams);

	return Constants.mapper.writeValueAsString(channel);

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

	URL url = null;

	try {
	    url = new URL(old);
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}

	final String host = url.getHost();

	String query = url.getQuery();

	final boolean hasQuery = query != null;

	String path = url.getPath();

	path = path.replace("-rj", "-rw");

	if (!hasQuery && path.startsWith("/vi/"))
	    path = path.replace("/vi/", "/vi_webp/").replace(".jpg", ".webp");

	return Constants.PROXY_PART + path + (hasQuery ? "?" + query + "&host=" : "?host=")
		+ URLUtils.silentEncode(host);

    }
}
