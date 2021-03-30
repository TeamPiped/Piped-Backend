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
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.ipfs.IPFS;
import me.kavin.piped.utils.obj.Channel;
import me.kavin.piped.utils.obj.ChapterSegment;
import me.kavin.piped.utils.obj.PipedStream;
import me.kavin.piped.utils.obj.Playlist;
import me.kavin.piped.utils.obj.SearchResults;
import me.kavin.piped.utils.obj.StreamItem;
import me.kavin.piped.utils.obj.Streams;
import me.kavin.piped.utils.obj.StreamsPage;
import me.kavin.piped.utils.obj.Subtitle;
import me.kavin.piped.utils.obj.search.SearchItem;
import me.kavin.piped.utils.obj.search.SearchStream;

public class ResponseHelper {

    public static final LoadingCache<String, CommentsInfo> commentsCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000)
            .build(key -> CommentsInfo.getInfo("https://www.youtube.com/watch?v=" + key));

    public static final byte[] streamsResponse(String videoId) throws Exception {

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

        final StreamInfo info = futureStream.get(10, TimeUnit.SECONDS);

//	System.out.println(Constants.mapper.writeValueAsString(info.getStreamSegments()));
        info.getSubtitles().forEach(subtitle -> subtitles
                .add(new Subtitle(rewriteURL(subtitle.getUrl()), subtitle.getFormat().getMimeType())));

        final List<PipedStream> videoStreams = new ObjectArrayList<>();
        final List<PipedStream> audioStreams = new ObjectArrayList<>();

        final String lbryURL = futureLBRY.get(10, TimeUnit.SECONDS);

        if (lbryURL != null)
            videoStreams.add(new PipedStream(lbryURL, "MP4", "LBRY", "video/mp4", false));

        final String hls;
        boolean livestream = false;

        if ((hls = info.getHlsUrl()) != null && !hls.isEmpty())
            livestream = true;

        if (hls != null) {

            java.util.stream.Stream<String> resp = Constants.h2client
                    .send(HttpRequest.newBuilder(URI.create(hls)).GET().build(), BodyHandlers.ofLines()).body();
            ObjectArrayList<String> lines = new ObjectArrayList<>();
            resp.forEach(line -> lines.add(line));

            for (int i = lines.size() - 1; i > 2; i--) {
                String line = lines.get(i);
                if (line.startsWith("https://manifest.googlevideo.com")) {
                    String prevLine = lines.get(i - 1);
                    String height = StringUtils.substringBetween(prevLine, "RESOLUTION=", ",").split("x")[1];
                    int fps = Integer.parseInt(StringUtils.substringBetween(prevLine, "FRAME-RATE=", ","));
                    String quality = height + "p";
                    if (fps > 30)
                        quality += fps;
                    videoStreams.add(new PipedStream(line, "HLS", quality, "application/x-mpegURL", false));
                }
            }
        }

        if (!livestream) {
            info.getVideoOnlyStreams().forEach(stream -> videoStreams.add(new PipedStream(rewriteURL(stream.getUrl()),
                    String.valueOf(stream.getFormat()), stream.getResolution(), stream.getFormat().getMimeType(), true,
                    stream.getBitrate(), stream.getInitStart(), stream.getInitEnd(), stream.getIndexStart(),
                    stream.getIndexEnd(), stream.getCodec(), stream.getWidth(), stream.getHeight(), 30)));
            info.getVideoStreams()
                    .forEach(stream -> videoStreams
                            .add(new PipedStream(rewriteURL(stream.getUrl()), String.valueOf(stream.getFormat()),
                                    stream.getResolution(), stream.getFormat().getMimeType(), false)));

            info.getAudioStreams()
                    .forEach(stream -> audioStreams.add(new PipedStream(rewriteURL(stream.getUrl()),
                            String.valueOf(stream.getFormat()), stream.getAverageBitrate() + " kbps",
                            stream.getFormat().getMimeType(), false, stream.getBitrate(), stream.getInitStart(),
                            stream.getInitEnd(), stream.getIndexStart(), stream.getIndexEnd(), stream.getCodec())));
        }

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedStreams().forEach(o -> {
            StreamInfoItem item = (StreamInfoItem) o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        List<ChapterSegment> segments = new ObjectArrayList<>();

        info.getStreamSegments().forEach(
                segment -> segments.add(new ChapterSegment(segment.getTitle(), segment.getStartTimeSeconds())));

        final Streams streams = new Streams(info.getName(), info.getDescription().getContent(),
                info.getTextualUploadDate(), info.getUploaderName(), info.getUploaderUrl().substring(23),
                rewriteURL(info.getUploaderAvatarUrl()), rewriteURL(info.getThumbnailUrl()), info.getDuration(),
                info.getViewCount(), info.getLikeCount(), info.getDislikeCount(), audioStreams, videoStreams,
                relatedStreams, subtitles, livestream, hls);

        return Constants.mapper.writeValueAsBytes(streams);

    }

    public static final byte[] channelResponse(String channelId) throws Exception {

        final ChannelInfo info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        String nextpage = null, id = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = page.getUrl();
            id = info.getNextPage().getId();
        }

        final Channel channel = new Channel(info.getId(), info.getName(), rewriteURL(info.getAvatarUrl()),
                rewriteURL(info.getBannerUrl()), info.getDescription(), nextpage, id, relatedStreams);

        IPFS.publishData(channel);

        return Constants.mapper.writeValueAsBytes(channel);

    }

    public static final byte[] channelPageResponse(String channelId, String url, String id)
            throws IOException, ExtractionException, InterruptedException {

        InfoItemsPage<StreamInfoItem> info = ChannelInfo.getMoreItems(Constants.YOUTUBE_SERVICE,
                "https://youtube.com/channel/" + channelId, new Page(url, id));

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        String nextpage = null, next_id = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = page.getUrl();
            next_id = info.getNextPage().getId();
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, next_id, relatedStreams);

        return Constants.mapper.writeValueAsBytes(streamspage);

    }

    public static final byte[] trendingResponse() throws ParsingException, ExtractionException, IOException {

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        String url = Constants.YOUTUBE_SERVICE.getKioskList().getListLinkHandlerFactoryByType("Trending")
                .getUrl("Trending");
        KioskInfo info = KioskInfo.getInfo(Constants.YOUTUBE_SERVICE, url);

        info.getRelatedItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        return Constants.mapper.writeValueAsBytes(relatedStreams);
    }

    public static final byte[] playlistResponse(String playlistId)
            throws IOException, ExtractionException, InterruptedException {

        final PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        String nextpage = null, next_id = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = page.getUrl();
            next_id = info.getNextPage().getId();
        }

        final Playlist playlist = new Playlist(info.getName(), rewriteURL(info.getThumbnailUrl()),
                rewriteURL(info.getBannerUrl()), nextpage, next_id, info.getUploaderName(),
                info.getUploaderUrl().substring(23), rewriteURL(info.getUploaderAvatarUrl()),
                (int) info.getStreamCount(), relatedStreams);

        return Constants.mapper.writeValueAsBytes(playlist);

    }

    public static final byte[] playlistPageResponse(String playlistId, String url, String id)
            throws IOException, ExtractionException, InterruptedException {

        InfoItemsPage<StreamInfoItem> info = PlaylistInfo.getMoreItems(Constants.YOUTUBE_SERVICE,
                "https://www.youtube.com/playlist?list=" + playlistId, new Page(url, id));

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        String nextpage = null, next_id = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = page.getUrl();
            next_id = info.getNextPage().getId();
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, next_id, relatedStreams);

        return Constants.mapper.writeValueAsBytes(streamspage);

    }

    public static final byte[] suggestionsResponse(String query)
            throws JsonProcessingException, IOException, ExtractionException {

        return Constants.mapper
                .writeValueAsBytes(Constants.YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query));

    }

    public static final byte[] searchResponse(String q) throws IOException, ExtractionException, InterruptedException {

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

        Page nextpage = info.getNextPage();

        return nextpage != null
                ? Constants.mapper.writeValueAsBytes(new SearchResults(nextpage.getUrl(), nextpage.getId(), items))
                : Constants.mapper.writeValueAsBytes(new SearchResults(null, null, items));

    }

    public static final byte[] searchPageResponse(String q, String url, String id)
            throws IOException, ExtractionException, InterruptedException {

        InfoItemsPage<InfoItem> pages = SearchInfo.getMoreItems(Constants.YOUTUBE_SERVICE,
                Constants.YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q), new Page(url, id));

        ObjectArrayList<SearchItem> items = new ObjectArrayList<>();

        pages.getItems().forEach(item -> {
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

        Page nextpage = pages.getNextPage();

        return nextpage != null
                ? Constants.mapper.writeValueAsBytes(new SearchResults(nextpage.getUrl(), nextpage.getId(), items))
                : Constants.mapper.writeValueAsBytes(new SearchResults(null, null, items));

    }

    public static final byte[] registerResponse(String user, String pass) throws IOException {

        return Constants.mapper.writeValueAsBytes(null);

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
