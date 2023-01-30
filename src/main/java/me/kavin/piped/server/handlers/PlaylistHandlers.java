package me.kavin.piped.server.handlers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.CollectionUtils.collectRelatedItems;
import static me.kavin.piped.utils.URLUtils.rewriteURL;
import static me.kavin.piped.utils.URLUtils.substringYouTube;

import com.google.errorprone.annotations.Var;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import io.sentry.Sentry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.server.handlers.auth.AuthPlaylistHandlers;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.obj.ContentItem;
import me.kavin.piped.utils.obj.Playlist;
import me.kavin.piped.utils.obj.StreamsPage;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.apache.commons.lang3.StringUtils;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public class PlaylistHandlers {
    public static byte[] playlistResponse(String playlistId) throws Exception {

        if (StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("playlistId is a required parameter"));

        if (playlistId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            return AuthPlaylistHandlers.playlistPipedResponse(playlistId);

        return playlistYouTubeResponse(playlistId);
    }

    private static byte[] playlistYouTubeResponse(String playlistId)
            throws IOException, ExtractionException {

        Sentry.setExtra("playlistId", playlistId);

         PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

         List<ContentItem> relatedStreams = collectRelatedItems(info.getRelatedItems());

        @Var String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

         var playlist = new Playlist(info.getName(), rewriteURL(info.getThumbnailUrl()),
                rewriteURL(info.getBannerUrl()), nextpage,
                info.getUploaderName().isEmpty() ? null : info.getUploaderName(),
                substringYouTube(info.getUploaderUrl()), rewriteURL(info.getUploaderAvatarUrl()),
                (int) info.getStreamCount(), relatedStreams);

        return mapper.writeValueAsBytes(playlist);

    }

    public static byte[] playlistPageResponse(String playlistId, String prevpageStr)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(prevpageStr))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("nextpage is a required parameter"));

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        if (prevpage == null)
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("nextpage is a required parameter"));

        ListExtractor.InfoItemsPage<StreamInfoItem> info = PlaylistInfo.getMoreItems(YOUTUBE_SERVICE,
                "https://www.youtube.com/playlist?list=" + playlistId, prevpage);

         List<ContentItem> relatedStreams = collectRelatedItems(info.getItems());

        @Var String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

         var streamspage = new StreamsPage(nextpage, relatedStreams);

        return mapper.writeValueAsBytes(streamspage);

    }

    public static byte[] playlistRSSResponse(String playlistId) throws Exception {

        if (StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("playlistId is a required parameter"));

        if (playlistId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            return AuthPlaylistHandlers.playlistPipedRSSResponse(playlistId);

        return playlistYouTubeRSSResponse(playlistId);
    }

    private static byte[] playlistYouTubeRSSResponse(String playlistId)
            throws IOException, ExtractionException, FeedException {

         PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

         List<SyndEntry> entries = new ObjectArrayList<>();

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle(info.getName());
        feed.setAuthor(info.getUploaderName());
        feed.setDescription(String.format("%s - Piped", info.getName()));
        feed.setLink(Constants.FRONTEND_URL + substringYouTube(info.getUrl()));
        feed.setPublishedDate(new Date());

        info.getRelatedItems().forEach(item -> {
            SyndEntry entry = new SyndEntryImpl();
            entry.setAuthor(item.getUploaderName());
            entry.setLink(item.getUrl());
            entry.setUri(item.getUrl());
            entry.setTitle(item.getName());
            entries.add(entry);
        });

        feed.setEntries(entries);

        return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);

    }

}
