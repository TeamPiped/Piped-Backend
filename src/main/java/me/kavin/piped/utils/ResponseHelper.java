package me.kavin.piped.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.ipfs.IPFS;
import me.kavin.piped.utils.obj.Channel;
import me.kavin.piped.utils.obj.Playlist;
import me.kavin.piped.utils.obj.*;
import me.kavin.piped.utils.obj.db.*;
import me.kavin.piped.utils.obj.search.SearchChannel;
import me.kavin.piped.utils.obj.search.SearchPlaylist;
import me.kavin.piped.utils.resp.*;
import okhttp3.FormBody;
import okhttp3.Request;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.URLUtils.*;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredContentCountry;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredLocalization;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;

public class ResponseHelper {

    public static byte[] streamsResponse(String videoId) throws Exception {

        final var futureStream = Multithreading.supplyAsync(() -> {
            try {
                return StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);
            } catch (Exception e) {
                ExceptionUtils.rethrow(e);
            }
            return null;
        });

        final var futureLbryId = Multithreading.supplyAsync(() -> {
            try {
                return LbryHelper.getLBRYId(videoId);
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
            return null;
        });

        final var futureLBRY = Multithreading.supplyAsync(() -> {
            try {
                String lbryId = futureLbryId.get(2, TimeUnit.SECONDS);

                return LbryHelper.getLBRYStreamURL(lbryId);
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
            return null;
        });

        final var futureDislikeRating = Multithreading.supplyAsync(() -> {
            try {
                return RydHelper.getDislikeRating(videoId);
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
            return null;
        });

        final List<Subtitle> subtitles = new ObjectArrayList<>();
        final List<ChapterSegment> chapters = new ObjectArrayList<>();

        final StreamInfo info = futureStream.get();

        info.getStreamSegments().forEach(segment -> chapters.add(new ChapterSegment(segment.getTitle(), rewriteURL(segment.getPreviewUrl()),
                segment.getStartTimeSeconds())));

        info.getSubtitles()
                .forEach(subtitle -> subtitles.add(new Subtitle(rewriteURL(subtitle.getContent()),
                        subtitle.getFormat().getMimeType(), subtitle.getDisplayLanguageName(),
                        subtitle.getLanguageTag(), subtitle.isAutoGenerated())));

        final List<PipedStream> videoStreams = new ObjectArrayList<>();
        final List<PipedStream> audioStreams = new ObjectArrayList<>();

        String lbryURL = null;

        try {
            lbryURL = futureLBRY.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignored
        }

        if (lbryURL != null)
            videoStreams.add(new PipedStream(lbryURL, "MP4", "LBRY", "video/mp4", false));

        boolean livestream = info.getStreamType() == StreamType.LIVE_STREAM;

        if (!livestream) {
            info.getVideoOnlyStreams().forEach(stream -> videoStreams.add(new PipedStream(rewriteVideoURL(stream.getContent()),
                    String.valueOf(stream.getFormat()), stream.getResolution(), stream.getFormat().getMimeType(), true,
                    stream.getBitrate(), stream.getInitStart(), stream.getInitEnd(), stream.getIndexStart(),
                    stream.getIndexEnd(), stream.getCodec(), stream.getWidth(), stream.getHeight(), 30)));
            info.getVideoStreams()
                    .forEach(stream -> videoStreams
                            .add(new PipedStream(rewriteVideoURL(stream.getContent()), String.valueOf(stream.getFormat()),
                                    stream.getResolution(), stream.getFormat().getMimeType(), false)));

            info.getAudioStreams()
                    .forEach(stream -> audioStreams.add(new PipedStream(rewriteVideoURL(stream.getContent()),
                            String.valueOf(stream.getFormat()), stream.getAverageBitrate() + " kbps",
                            stream.getFormat().getMimeType(), false, stream.getBitrate(), stream.getInitStart(),
                            stream.getInitEnd(), stream.getIndexStart(), stream.getIndexEnd(), stream.getCodec())));
        }

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> {
            if (o instanceof StreamInfoItem)
                relatedStreams.add(collectRelatedStream(o));
        });

        long time = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        if (info.getUploadDate() != null && System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
            updateVideo(info.getId(), info, time);

        String lbryId;

        try {
            lbryId = futureLbryId.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            lbryId = null;
        }

        // Attempt to get dislikes calculating with the RYD API rating
        if (info.getDislikeCount() < 0 && info.getLikeCount() >= 0) {
            double rating;
            try {
                rating = futureDislikeRating.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                rating = -1;
            }

            if (rating > 1 && rating <= 5) {
                info.setDislikeCount(Math.round(info.getLikeCount() * ((5 - rating) / (rating - 1))));
            }
        }

        final Streams streams = new Streams(info.getName(), info.getDescription().getContent(),
                info.getTextualUploadDate(), info.getUploaderName(), substringYouTube(info.getUploaderUrl()),
                rewriteURL(info.getUploaderAvatarUrl()), rewriteURL(info.getThumbnailUrl()), info.getDuration(),
                info.getViewCount(), info.getLikeCount(), info.getDislikeCount(), info.getUploaderSubscriberCount(), info.isUploaderVerified(),
                audioStreams, videoStreams, relatedStreams, subtitles, livestream, rewriteVideoURL(info.getHlsUrl()),
                rewriteVideoURL(info.getDashMpdUrl()), lbryId, chapters);

        return mapper.writeValueAsBytes(streams);

    }

    public static byte[] resolveClipId(String clipId) throws Exception {

        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(
                        getPreferredLocalization(), getPreferredContentCountry())
                        .value("url", "https://www.youtube.com/clip/" + clipId)
                        .done())
                .getBytes(UTF_8);

        final JsonObject jsonResponse = getJsonPostResponse("navigation/resolve_url",
                body, getPreferredLocalization());

        final String videoId = JsonUtils.getString(jsonResponse, "endpoint.watchEndpoint.videoId");

        return mapper.writeValueAsBytes(new VideoResolvedResponse(videoId));
    }

    public static byte[] channelResponse(String channelPath) throws Exception {

        final ChannelInfo info = ChannelInfo.getInfo("https://youtube.com/" + channelPath);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        Multithreading.runAsync(() -> {

            me.kavin.piped.utils.obj.db.Channel channel = DatabaseHelper.getChannelFromId(info.getId());

            try (Session s = DatabaseSessionFactory.createSession()) {

                if (channel != null) {
                    if (channel.isVerified() != info.isVerified()
                            || !channel.getUploaderAvatar().equals(info.getAvatarUrl())) {
                        channel.setVerified(info.isVerified());
                        channel.setUploaderAvatar(info.getAvatarUrl());
                        var tr = s.beginTransaction();
                        s.merge(channel);
                        tr.commit();
                    }
                    for (StreamInfoItem item : info.getRelatedItems()) {
                        long time = item.getUploadDate() != null
                                ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                : System.currentTimeMillis();
                        if (System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
                            try {
                                String id = YOUTUBE_SERVICE.getStreamLHFactory().getId(item.getUrl());
                                updateVideo(id, item, time, true);
                            } catch (Exception e) {
                                ExceptionHandler.handle(e);
                            }
                    }
                }
            }
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        final Channel channel = new Channel(info.getId(), info.getName(), rewriteURL(info.getAvatarUrl()),
                rewriteURL(info.getBannerUrl()), info.getDescription(), info.getSubscriberCount(), info.isVerified(),
                nextpage, relatedStreams);

        IPFS.publishData(channel);

        return mapper.writeValueAsBytes(channel);

    }

    public static byte[] channelPageResponse(String channelId, String prevpageStr)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(prevpageStr))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<StreamInfoItem> info = ChannelInfo.getMoreItems(YOUTUBE_SERVICE,
                "https://youtube.com/channel/" + channelId, prevpage);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, relatedStreams);

        return mapper.writeValueAsBytes(streamspage);

    }

    public static byte[] trendingResponse(String region)
            throws ExtractionException, IOException {

        if (region == null)
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        KioskList kioskList = YOUTUBE_SERVICE.getKioskList();
        kioskList.forceContentCountry(new ContentCountry(region));
        KioskExtractor<?> extractor = kioskList.getDefaultKioskExtractor();
        extractor.fetchPage();
        KioskInfo info = KioskInfo.getInfo(extractor);

        info.getRelatedItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        return mapper.writeValueAsBytes(relatedStreams);
    }

    public static byte[] playlistResponse(String playlistId) throws ExtractionException, IOException {

        if (StringUtils.isBlank(playlistId))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        if (playlistId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            return playlistPipedResponse(playlistId);

        return playlistYouTubeResponse(playlistId);
    }

    private static byte[] playlistPipedResponse(String playlistId) throws IOException {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var cb = s.getCriteriaBuilder();
            var cq = cb.createQuery(me.kavin.piped.utils.obj.db.Playlist.class);
            var root = cq.from(me.kavin.piped.utils.obj.db.Playlist.class);
            root.fetch("videos", JoinType.LEFT)
                    .fetch("channel", JoinType.LEFT);
            root.fetch("owner", JoinType.LEFT);
            cq.select(root);
            cq.where(cb.equal(root.get("playlist_id"), UUID.fromString(playlistId)));
            var query = s.createQuery(cq);
            var pl = query.uniqueResult();

            if (pl == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            final List<StreamItem> relatedStreams = new ObjectArrayList<>();

            var videos = pl.getVideos();

            for (var video : videos) {
                var channel = video.getChannel();
                relatedStreams.add(new StreamItem("/watch?v=" + video.getId(), video.getTitle(), rewriteURL(video.getThumbnail()), channel.getUploader(),
                        "/channel/" + channel.getUploaderId(), rewriteURL(channel.getUploaderAvatar()), null, null,
                        video.getDuration(), -1, -1, channel.isVerified()));
            }

            final Playlist playlist = new Playlist(pl.getName(), rewriteURL(pl.getThumbnail()), null, null, pl.getOwner().getUsername(),
                    null, null, videos.size(), relatedStreams);

            return mapper.writeValueAsBytes(playlist);
        }
    }

    private static byte[] playlistYouTubeResponse(String playlistId)
            throws IOException, ExtractionException {

        final PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        final Playlist playlist = new Playlist(info.getName(), rewriteURL(info.getThumbnailUrl()),
                rewriteURL(info.getBannerUrl()), nextpage,
                info.getUploaderName().isEmpty() ? null : info.getUploaderName(),
                substringYouTube(info.getUploaderUrl()), rewriteURL(info.getUploaderAvatarUrl()),
                (int) info.getStreamCount(), relatedStreams);

        return mapper.writeValueAsBytes(playlist);

    }

    public static byte[] playlistPageResponse(String playlistId, String prevpageStr)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(prevpageStr))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<StreamInfoItem> info = PlaylistInfo.getMoreItems(YOUTUBE_SERVICE,
                "https://www.youtube.com/playlist?list=" + playlistId, prevpage);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, relatedStreams);

        return mapper.writeValueAsBytes(streamspage);

    }

    public static byte[] playlistRSSResponse(String playlistId) throws ExtractionException, IOException, FeedException {

        if (StringUtils.isBlank(playlistId))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        if (playlistId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            return playlistPipedRSSResponse(playlistId);

        return playlistYouTubeRSSResponse(playlistId);
    }

    private static byte[] playlistPipedRSSResponse(String playlistId)
            throws FeedException {

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var cb = s.getCriteriaBuilder();
            var cq = cb.createQuery(me.kavin.piped.utils.obj.db.Playlist.class);
            var root = cq.from(me.kavin.piped.utils.obj.db.Playlist.class);
            root.fetch("videos", JoinType.LEFT)
                    .fetch("channel", JoinType.LEFT);
            root.fetch("owner", JoinType.LEFT);
            cq.select(root);
            cq.where(cb.equal(root.get("playlist_id"), UUID.fromString(playlistId)));
            var query = s.createQuery(cq);
            var pl = query.uniqueResult();

            final List<SyndEntry> entries = new ObjectArrayList<>();

            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("rss_2.0");
            feed.setTitle(pl.getName());
            feed.setAuthor(pl.getOwner().getUsername());
            feed.setDescription(String.format("%s - Piped", pl.getName()));
            feed.setLink(Constants.FRONTEND_URL + "/playlist?list=" + pl.getPlaylistId());
            feed.setPublishedDate(new Date());

            for (var video : pl.getVideos()) {
                SyndEntry entry = new SyndEntryImpl();
                entry.setAuthor(video.getChannel().getUploader());
                entry.setLink(Constants.FRONTEND_URL + "/video?id=" + video.getId());
                entry.setUri(Constants.FRONTEND_URL + "/video?id=" + video.getId());
                entry.setTitle(video.getTitle());
                entries.add(entry);
            }

            feed.setEntries(entries);

            return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);
        }
    }

    private static byte[] playlistYouTubeRSSResponse(String playlistId)
            throws IOException, ExtractionException, FeedException {

        final PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

        final List<SyndEntry> entries = new ObjectArrayList<>();

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

    public static byte[] suggestionsResponse(String query)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(query))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        return mapper.writeValueAsBytes(YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query));

    }

    public static byte[] opensearchSuggestionsResponse(String query)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(query))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        return mapper.writeValueAsBytes(Arrays.asList(
                query,
                YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query)
        ));

    }

    public static byte[] searchResponse(String q, String filter)
            throws IOException, ExtractionException {

        final SearchInfo info = SearchInfo.getInfo(YOUTUBE_SERVICE,
                YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q, Collections.singletonList(filter), null));

        ObjectArrayList<Object> items = new ObjectArrayList<>();

        info.getRelatedItems().forEach(item -> {
            switch (item.getInfoType()) {
                case STREAM -> items.add(collectRelatedStream(item));
                case CHANNEL -> {
                    ChannelInfoItem channel = (ChannelInfoItem) item;
                    items.add(new SearchChannel(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), channel.getDescription(), channel.getSubscriberCount(),
                            channel.getStreamCount(), channel.isVerified()));
                }
                case PLAYLIST -> {
                    PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                    items.add(new SearchPlaylist(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), playlist.getUploaderName(), playlist.getStreamCount()));
                }
            }
        });

        Page nextpage = info.getNextPage();

        return mapper.writeValueAsBytes(new SearchResults(items,
                mapper.writeValueAsString(nextpage), info.getSearchSuggestion(), info.isCorrectedSearch()));

    }

    public static byte[] searchPageResponse(String q, String filter, String prevpageStr)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(prevpageStr))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<InfoItem> pages = SearchInfo.getMoreItems(YOUTUBE_SERVICE,
                YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q, Collections.singletonList(filter), null), prevpage);

        ObjectArrayList<Object> items = new ObjectArrayList<>();

        pages.getItems().forEach(item -> {
            switch (item.getInfoType()) {
                case STREAM -> items.add(collectRelatedStream(item));
                case CHANNEL -> {
                    ChannelInfoItem channel = (ChannelInfoItem) item;
                    items.add(new SearchChannel(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), channel.getDescription(), channel.getSubscriberCount(),
                            channel.getStreamCount(), channel.isVerified()));
                }
                case PLAYLIST -> {
                    PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                    items.add(new SearchPlaylist(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), playlist.getUploaderName(), playlist.getStreamCount()));
                }
            }
        });

        Page nextpage = pages.getNextPage();

        return mapper
                .writeValueAsBytes(new SearchResults(items, mapper.writeValueAsString(nextpage)));

    }

    public static byte[] commentsResponse(String videoId) throws Exception {

        CommentsInfo info = CommentsInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);

        List<Comment> comments = new ObjectArrayList<>();

        info.getRelatedItems().forEach(comment -> {
            try {
                String repliespage = null;
                if (comment.getReplies() != null)
                    repliespage = mapper.writeValueAsString(comment.getReplies());

                comments.add(new Comment(comment.getUploaderName(), rewriteURL(comment.getUploaderAvatarUrl()),
                        comment.getCommentId(), comment.getCommentText(), comment.getTextualUploadDate(),
                        substringYouTube(comment.getUploaderUrl()), repliespage, comment.getLikeCount(),
                        comment.isHeartedByUploader(), comment.isPinned(), comment.isUploaderVerified()));
            } catch (JsonProcessingException e) {
                ExceptionHandler.handle(e);
            }
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        CommentsPage commentsItem = new CommentsPage(comments, nextpage, info.isCommentsDisabled());

        return mapper.writeValueAsBytes(commentsItem);

    }

    public static byte[] commentsPageResponse(String videoId, String prevpageStr) throws Exception {

        if (StringUtils.isEmpty(prevpageStr))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<CommentsInfoItem> info = CommentsInfo.getMoreItems(YOUTUBE_SERVICE, "https://www.youtube.com/watch?v=" + videoId, prevpage);

        List<Comment> comments = new ObjectArrayList<>();

        info.getItems().forEach(comment -> {
            try {
                String repliespage = null;
                if (comment.getReplies() != null)
                    repliespage = mapper.writeValueAsString(comment.getReplies());

                comments.add(new Comment(comment.getUploaderName(), rewriteURL(comment.getUploaderAvatarUrl()),
                        comment.getCommentId(), comment.getCommentText(), comment.getTextualUploadDate(),
                        substringYouTube(comment.getUploaderUrl()), repliespage, comment.getLikeCount(),
                        comment.isHeartedByUploader(), comment.isPinned(), comment.isUploaderVerified()));
            } catch (JsonProcessingException e) {
                ExceptionHandler.handle(e);
            }
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        CommentsPage commentsItem = new CommentsPage(comments, nextpage, false);

        return mapper.writeValueAsBytes(commentsItem);

    }

    public static byte[] registerResponse(String user, String pass) throws IOException {

        if (Constants.DISABLE_REGISTRATION)
            return mapper.writeValueAsBytes(new DisabledRegistrationResponse());

        if (StringUtils.isBlank(user) || StringUtils.isBlank(pass))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        if (user.length() > 24)
            return mapper.writeValueAsBytes(
                    mapper.createObjectNode()
                            .put("error", "The username must be less than 24 characters")
            );

        user = user.toLowerCase();

        try (Session s = DatabaseSessionFactory.createSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<User> cr = cb.createQuery(User.class);
            Root<User> root = cr.from(User.class);
            cr.select(root).where(cb.equal(root.get("username"), user));
            boolean registered = s.createQuery(cr).uniqueResult() != null;

            if (registered)
                return mapper.writeValueAsBytes(new AlreadyRegisteredResponse());

            if (Constants.COMPROMISED_PASSWORD_CHECK) {
                String sha1Hash = DigestUtils.sha1Hex(pass).toUpperCase();
                String prefix = sha1Hash.substring(0, 5);
                String suffix = sha1Hash.substring(5);
                String[] entries = RequestUtils
                        .sendGet("https://api.pwnedpasswords.com/range/" + prefix, "github.com/TeamPiped/Piped-Backend")
                        .split("\n");
                for (String entry : entries)
                    if (StringUtils.substringBefore(entry, ":").equals(suffix))
                        return mapper.writeValueAsBytes(new CompromisedPasswordResponse());
            }

            User newuser = new User(user, argon2PasswordEncoder.encode(pass), Set.of());

            var tr = s.beginTransaction();
            s.persist(newuser);
            tr.commit();


            return mapper.writeValueAsBytes(new LoginResponse(newuser.getSessionId()));
        }
    }

    private static final Argon2PasswordEncoder argon2PasswordEncoder = new Argon2PasswordEncoder();

    private static final BCryptPasswordEncoder bcryptPasswordEncoder = new BCryptPasswordEncoder();

    private static boolean hashMatch(String hash, String pass) {
        return hash.startsWith("$argon2") ?
                argon2PasswordEncoder.matches(pass, hash) :
                bcryptPasswordEncoder.matches(pass, hash);
    }

    public static byte[] loginResponse(String user, String pass)
            throws IOException {

        if (user == null || pass == null)
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        user = user.toLowerCase();

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<User> cr = cb.createQuery(User.class);
            Root<User> root = cr.from(User.class);
            cr.select(root).where(root.get("username").in(user));

            User dbuser = s.createQuery(cr).uniqueResult();

            if (dbuser != null) {
                String hash = dbuser.getPassword();
                if (hashMatch(hash, pass)) {
                    return mapper.writeValueAsBytes(new LoginResponse(dbuser.getSessionId()));
                }
            }

            return mapper.writeValueAsBytes(new IncorrectCredentialsResponse());
        }
    }

    public static byte[] deleteUserResponse(String session, String pass) throws IOException {

        if (StringUtils.isBlank(pass))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            User user = DatabaseHelper.getUserFromSession(session);

            if (user == null)
                return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

            String hash = user.getPassword();

            if (!hashMatch(hash, pass))
                return mapper.writeValueAsBytes(new IncorrectCredentialsResponse());

            try {
                var tr = s.beginTransaction();
                s.remove(user);
                tr.commit();
            } catch (Exception e) {
                return mapper.writeValueAsBytes(new ErrorResponse(ExceptionUtils.getStackTrace(e), e.getMessage()));
            }

            return mapper.writeValueAsBytes(new DeleteUserResponse(user.getUsername()));
        }
    }

    public static byte[] subscribeResponse(String session, String channelId)
            throws IOException {

        try (Session s = DatabaseSessionFactory.createSession()) {

            User user = DatabaseHelper.getUserFromSessionWithSubscribed(session);

            if (user != null) {
                if (!user.getSubscribed().contains(channelId)) {

                    user.getSubscribed().add(channelId);

                    var tr = s.beginTransaction();
                    s.merge(user);
                    tr.commit();

                    Multithreading.runAsync(() -> {
                        var channel = DatabaseHelper.getChannelFromId(channelId);
                        if (channel == null) {
                            Multithreading.runAsync(() -> saveChannel(channelId));
                        }
                    });
                }

                return mapper.writeValueAsBytes(new AcceptedResponse());
            }


            return mapper.writeValueAsBytes(new AuthenticationFailureResponse());
        }

    }

    public static byte[] unsubscribeResponse(String session, String channelId)
            throws IOException {

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {
            try (Session s = DatabaseSessionFactory.createSession()) {
                var tr = s.beginTransaction();
                s.createNativeMutationQuery("delete from users_subscribed where subscriber = :id and channel = :channel")
                        .setParameter("id", user.getId()).setParameter("channel", channelId).executeUpdate();
                tr.commit();
                return mapper.writeValueAsBytes(new AcceptedResponse());
            }

        }

        return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] isSubscribedResponse(String session, String channelId) throws IOException {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var cb = s.getCriteriaBuilder();
            var query = cb.createQuery(Long.class);
            var root = query.from(User.class);
            query.select(cb.count(root))
                    .where(cb.and(
                            cb.equal(root.get("sessionId"), session),
                            cb.isMember(channelId, root.get("subscribed_ids"))
                    ));
            var subscribed = s.createQuery(query).getSingleResult() > 0;

            return mapper.writeValueAsBytes(new SubscribeStatusResponse(subscribed));
        }
    }

    public static byte[] feedResponse(String session) throws IOException {

        if (StringUtils.isBlank(session))
            return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                CriteriaBuilder cb = s.getCriteriaBuilder();

                // Get all videos from subscribed channels, with channel info
                CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
                var root = criteria.from(Video.class);
                root.fetch("channel", JoinType.INNER);
                var subquery = criteria.subquery(User.class);
                var subroot = subquery.from(User.class);
                subquery.select(subroot.get("subscribed_ids"))
                        .where(cb.equal(subroot.get("id"), user.getId()));

                criteria.select(root)
                        .where(
                                root.get("channel").in(subquery)
                        )
                        .orderBy(cb.desc(root.get("uploaded")));

                List<StreamItem> feedItems = new ObjectArrayList<>();

                for (Video video : s.createQuery(criteria).setTimeout(20).list()) {
                    var channel = video.getChannel();

                    feedItems.add(new StreamItem("/watch?v=" + video.getId(), video.getTitle(),
                            rewriteURL(video.getThumbnail()), channel.getUploader(), "/channel/" + channel.getUploaderId(),
                            rewriteURL(channel.getUploaderAvatar()), null, null, video.getDuration(), video.getViews(),
                            video.getUploaded(), channel.isVerified()));
                }

                return mapper.writeValueAsBytes(feedItems);
            }
        }

        return mapper.writeValueAsBytes(new AuthenticationFailureResponse());
    }

    public static byte[] feedResponseRSS(String session) throws IOException, FeedException {

        if (StringUtils.isBlank(session))
            return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {

            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                SyndFeed feed = new SyndFeedImpl();
                feed.setFeedType("atom_1.0");
                feed.setTitle("Piped - Feed");
                feed.setDescription(String.format("Piped's RSS subscription feed for %s.", user.getUsername()));
                feed.setUri(Constants.FRONTEND_URL + "/feed");
                feed.setPublishedDate(new Date());

                CriteriaBuilder cb = s.getCriteriaBuilder();

                // Get all videos from subscribed channels, with channel info
                CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
                var root = criteria.from(Video.class);
                root.fetch("channel", JoinType.INNER);
                var subquery = criteria.subquery(User.class);
                var subroot = subquery.from(User.class);
                subquery.select(subroot.get("subscribed_ids"))
                        .where(cb.equal(subroot.get("id"), user.getId()));

                criteria.select(root)
                        .where(
                                root.get("channel").in(subquery)
                        )
                        .orderBy(cb.desc(root.get("uploaded")));

                List<Video> videos = s.createQuery(criteria)
                        .setTimeout(20)
                        .setMaxResults(100)
                        .list();

                final List<SyndEntry> entries = new ObjectArrayList<>();

                for (Video video : videos) {
                    var channel = video.getChannel();
                    SyndEntry entry = new SyndEntryImpl();

                    SyndPerson person = new SyndPersonImpl();
                    person.setName(channel.getUploader());
                    person.setUri(Constants.FRONTEND_URL + "/channel/" + channel.getUploaderId());

                    entry.setAuthors(Collections.singletonList(person));

                    entry.setLink(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                    entry.setUri(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                    entry.setTitle(video.getTitle());
                    entry.setPublishedDate(new Date(video.getUploaded()));
                    entries.add(entry);
                }

                feed.setEntries(entries);

                return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);
            }
        }

        return mapper.writeValueAsBytes(new AuthenticationFailureResponse());
    }

    public static byte[] unauthenticatedFeedResponse(String[] channelIds) throws Exception {

        Set<String> filtered = Arrays.stream(channelIds)
                .filter(StringUtils::isNotBlank)
                .filter(id -> id.matches("[A-Za-z\\d_-]+"))
                .collect(Collectors.toUnmodifiableSet());

        if (filtered.isEmpty())
            return mapper.writeValueAsBytes(Collections.EMPTY_LIST);

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            CriteriaBuilder cb = s.getCriteriaBuilder();

            // Get all videos from subscribed channels, with channel info
            CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
            var root = criteria.from(Video.class);
            root.fetch("channel", JoinType.INNER);

            criteria.select(root)
                    .where(cb.and(
                            root.get("channel").get("id").in(filtered)
                    ))
                    .orderBy(cb.desc(root.get("uploaded")));

            List<StreamItem> feedItems = new ObjectArrayList<>();

            for (Video video : s.createQuery(criteria).setTimeout(20).list()) {
                var channel = video.getChannel();

                feedItems.add(new StreamItem("/watch?v=" + video.getId(), video.getTitle(),
                        rewriteURL(video.getThumbnail()), channel.getUploader(), "/channel/" + channel.getUploaderId(),
                        rewriteURL(channel.getUploaderAvatar()), null, null, video.getDuration(), video.getViews(),
                        video.getUploaded(), channel.isVerified()));
            }

            updateSubscribedTime(filtered);
            addMissingChannels(filtered);

            return mapper.writeValueAsBytes(feedItems);
        }
    }

    public static byte[] unauthenticatedFeedResponseRSS(String[] channelIds) throws Exception {

        Set<String> filtered = Arrays.stream(channelIds)
                .filter(StringUtils::isNotBlank)
                .filter(id -> id.matches("[A-Za-z\\d_-]+"))
                .collect(Collectors.toUnmodifiableSet());

        if (filtered.isEmpty())
            return mapper.writeValueAsBytes(mapper.createObjectNode()
                    .put("error", "No valid channel IDs provided"));

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            CriteriaBuilder cb = s.getCriteriaBuilder();

            // Get all videos from subscribed channels, with channel info
            CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
            var root = criteria.from(Video.class);
            root.fetch("channel", JoinType.INNER);

            criteria.select(root)
                    .where(cb.and(
                            root.get("channel").get("id").in(filtered)
                    ))
                    .orderBy(cb.desc(root.get("uploaded")));

            List<Video> videos = s.createQuery(criteria)
                    .setTimeout(20)
                    .setMaxResults(100)
                    .list();

            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("atom_1.0");
            feed.setTitle("Piped - Feed");
            feed.setDescription("Piped's RSS unauthenticated subscription feed.");
            feed.setUri(Constants.FRONTEND_URL + "/feed");
            feed.setPublishedDate(new Date());

            final List<SyndEntry> entries = new ObjectArrayList<>();

            for (Video video : videos) {
                var channel = video.getChannel();
                SyndEntry entry = new SyndEntryImpl();

                SyndPerson person = new SyndPersonImpl();
                person.setName(channel.getUploader());
                person.setUri(Constants.FRONTEND_URL + "/channel/" + channel.getUploaderId());

                entry.setAuthors(Collections.singletonList(person));

                entry.setLink(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                entry.setUri(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                entry.setTitle(video.getTitle());
                entry.setPublishedDate(new Date(video.getUploaded()));
                entries.add(entry);
            }

            feed.setEntries(entries);

            updateSubscribedTime(filtered);
            addMissingChannels(filtered);

            return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);
        }
    }

    private static void updateSubscribedTime(Collection<String> channelIds) {
        Multithreading.runAsync(() -> {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                var tr = s.beginTransaction();
                var cb = s.getCriteriaBuilder();
                var cu = cb.createCriteriaUpdate(UnauthenticatedSubscription.class);
                var root = cu.getRoot();
                cu.where(root.get("id").in(channelIds))
                        .set(root.get("subscribedAt"), System.currentTimeMillis())
                        .where(cb.lt(root.get("subscribedAt"), System.currentTimeMillis() - (TimeUnit.DAYS.toMillis(Constants.SUBSCRIPTIONS_EXPIRY) / 2)));
                s.createMutationQuery(cu).executeUpdate();
                tr.commit();
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    private static void addMissingChannels(Collection<String> channelIds) {
        Multithreading.runAsyncLimited(() -> {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                var cb = s.getCriteriaBuilder();

                {
                    var query = cb.createQuery();
                    var root = query.from(UnauthenticatedSubscription.class);
                    query.select(root.get("id"))
                            .where(root.get("id").in(channelIds));

                    List<Object> existing = s.createQuery(query).setTimeout(20).list();

                    var tr = s.beginTransaction();
                    channelIds.stream()
                            .filter(id -> !existing.contains(id))
                            .map(UnauthenticatedSubscription::new)
                            .forEach(s::insert);
                    tr.commit();
                }

                {
                    var query = cb.createQuery();
                    var root = query.from(me.kavin.piped.utils.obj.db.Channel.class);
                    query.select(root.get("id"))
                            .where(root.get("id").in(channelIds));

                    List<Object> existing = s.createQuery(query).setTimeout(20).list();

                    channelIds.stream()
                            .filter(id -> !existing.contains(id))
                            .forEach(id -> Multithreading.runAsyncLimited(() -> saveChannel(id)));
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    public static byte[] importResponse(String session, String[] channelIds, boolean override) throws IOException {


        User user = DatabaseHelper.getUserFromSessionWithSubscribed(session);

        if (user != null) {

            Multithreading.runAsync(() -> {
                try (Session s = DatabaseSessionFactory.createSession()) {
                    if (override) {
                        user.setSubscribed(Set.of(channelIds));
                    } else {
                        for (String channelId : channelIds)
                            user.getSubscribed().add(channelId);
                    }

                    if (channelIds.length > 0) {
                        var tr = s.beginTransaction();
                        s.merge(user);
                        tr.commit();
                    }
                } catch (Exception e) {
                    ExceptionHandler.handle(e);
                }
            });

            Multithreading.runAsync(() -> {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                    var channels = DatabaseHelper.getChannelsFromIds(s, Arrays.asList(channelIds));

                    Arrays.stream(channelIds).parallel()
                            .filter(channelId ->
                                    channels.stream().parallel()
                                            .filter(channel -> channel.getUploaderId().equals(channelId))
                                            .findFirst().isEmpty()
                            )
                            .forEach(channelId -> Multithreading.runAsyncLimited(() -> saveChannel(channelId)));
                } catch (Exception e) {
                    ExceptionHandler.handle(e);
                }
            });

            return mapper.writeValueAsBytes(new AcceptedResponse());
        }

        return mapper.writeValueAsBytes(new AuthenticationFailureResponse());
    }

    public static byte[] subscriptionsResponse(String session)
            throws IOException {

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                CriteriaBuilder cb = s.getCriteriaBuilder();
                var query = cb.createQuery(me.kavin.piped.utils.obj.db.Channel.class);
                var root = query.from(me.kavin.piped.utils.obj.db.Channel.class);
                var subquery = query.subquery(User.class);
                var subroot = subquery.from(User.class);

                subquery.select(subroot.get("subscribed_ids"))
                        .where(cb.equal(subroot.get("id"), user.getId()));

                query.select(root)
                        .where(root.get("uploader_id").in(subquery));

                var channels = s.createQuery(query).list();

                List<SubscriptionChannel> subscriptionItems = channels
                        .stream().parallel()
                        .sorted(Comparator.comparing(me.kavin.piped.utils.obj.db.Channel::getUploader, String.CASE_INSENSITIVE_ORDER))
                        .map(channel -> new SubscriptionChannel("/channel/" + channel.getUploaderId(),
                                channel.getUploader(), rewriteURL(channel.getUploaderAvatar()), channel.isVerified()))
                        .toList();

                return mapper.writeValueAsBytes(subscriptionItems);
            }
        }

        return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] unauthenticatedSubscriptionsResponse(String[] channelIds)
            throws IOException {

        Set<String> filtered = Arrays.stream(channelIds)
                .filter(StringUtils::isNotBlank)
                .filter(id -> id.matches("[A-Za-z\\d_-]+"))
                .collect(Collectors.toUnmodifiableSet());

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            CriteriaBuilder cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Channel.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Channel.class);
            query.select(root);
            query.where(root.get("uploader_id").in(filtered));

            var channels = s.createQuery(query).list();

            List<SubscriptionChannel> subscriptionItems = channels
                    .stream().parallel()
                    .sorted(Comparator.comparing(me.kavin.piped.utils.obj.db.Channel::getUploader, String.CASE_INSENSITIVE_ORDER))
                    .map(channel -> new SubscriptionChannel("/channel/" + channel.getUploaderId(),
                            channel.getUploader(), rewriteURL(channel.getUploaderAvatar()), channel.isVerified()))
                    .toList();

            return mapper.writeValueAsBytes(subscriptionItems);
        }
    }

    public static byte[] createPlaylist(String session, String name) throws IOException {

        if (StringUtils.isBlank(name))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var playlist = new me.kavin.piped.utils.obj.db.Playlist(name, user, "https://i.ytimg.com/");

            var tr = s.beginTransaction();
            s.persist(playlist);
            tr.commit();

            ObjectNode response = mapper.createObjectNode();
            response.put("playlistId", String.valueOf(playlist.getPlaylistId()));

            return mapper.writeValueAsBytes(response);
        }
    }

    public static byte[] deletePlaylistResponse(String session, String playlistId) throws IOException {

        if (StringUtils.isBlank(playlistId))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var playlist = DatabaseHelper.getPlaylistFromId(s, playlistId);

            if (playlist == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            if (playlist.getOwner().getId() != user.getId())
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "You do not own this playlist"));

            var tr = s.beginTransaction();
            s.remove(playlist);
            tr.commit();

        }

        return mapper.writeValueAsBytes(new AcceptedResponse());
    }

    public static byte[] playlistsResponse(String session) throws IOException {

        try (Session s = DatabaseSessionFactory.createSession()) {

            User user = DatabaseHelper.getUserFromSession(session, s);

            if (user == null)
                return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

            var playlists = new ObjectArrayList<>();

            for (var playlist : user.getPlaylists()) {
                ObjectNode node = mapper.createObjectNode();
                node.put("id", String.valueOf(playlist.getPlaylistId()));
                node.put("name", playlist.getName());
                node.put("shortDescription", playlist.getShortDescription());
                node.put("thumbnail", rewriteURL(playlist.getThumbnail()));
                playlists.add(node);
            }

            return mapper.writeValueAsBytes(playlists);
        }
    }

    public static byte[] importPlaylistResponse(String session, String playlistId) throws IOException, ExtractionException {

        if (StringUtils.isBlank(playlistId))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        var user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

        final String url = "https://www.youtube.com/playlist?list=" + playlistId;

        PlaylistInfo info = PlaylistInfo.getInfo(url);

        var playlist = new me.kavin.piped.utils.obj.db.Playlist(info.getName(), user, info.getThumbnailUrl());

        List<StreamInfoItem> videos = new ObjectArrayList<>(info.getRelatedItems());

        Page nextpage = info.getNextPage();

        while (nextpage != null) {
            var page = PlaylistInfo.getMoreItems(YOUTUBE_SERVICE, url, nextpage);
            videos.addAll(page.getItems());

            nextpage = page.getNextPage();
        }

        Set<String> channelIds = videos.stream()
                .map(StreamInfoItem::getUploaderUrl)
                .map(URLUtils::substringYouTube)
                .map(s -> s.substring("/channel/".length()))
                .collect(Collectors.toUnmodifiableSet());
        List<String> videoIds = videos.stream()
                .map(StreamInfoItem::getUrl)
                .map(URLUtils::substringYouTube)
                .map(s -> s.substring("/watch?v=".length()))
                .toList();

        try (Session s = DatabaseSessionFactory.createSession()) {

            Map<String, me.kavin.piped.utils.obj.db.Channel> channelMap = new Object2ObjectOpenHashMap<>();

            var channels = DatabaseHelper.getChannelsFromIds(s, channelIds);
            channelIds.forEach(id -> {
                var fetched = channels.stream().filter(channel -> channel.getUploaderId().equals(id)).findFirst()
                        .orElseGet(() -> saveChannel(id));
                channelMap.put(id, fetched);
            });

            Map<String, PlaylistVideo> videoMap = new Object2ObjectOpenHashMap<>();

            var playlistVideos = DatabaseHelper.getPlaylistVideosFromIds(s, videoIds);
            videoIds.forEach(id ->
                    playlistVideos.stream().filter(video -> video.getId().equals(id)).findFirst()
                            .ifPresent(playlistVideo -> videoMap.put(id, playlistVideo))
            );

            videos.forEach(video -> {
                var channelId = substringYouTube(video.getUploaderUrl()).substring("/channel/".length());
                var videoId = substringYouTube(video.getUrl()).substring("/watch?v=".length());

                var channel = channelMap.get(channelId);

                playlist.getVideos().add(videoMap.computeIfAbsent(videoId, (key) -> new PlaylistVideo(videoId, video.getName(), video.getThumbnailUrl(), video.getDuration(), channel)));
            });

            var tr = s.beginTransaction();
            s.persist(playlist);
            tr.commit();
        }

        return mapper.writeValueAsBytes(mapper.createObjectNode()
                .put("playlistId", String.valueOf(playlist.getPlaylistId()))
        );
    }

    public static byte[] addToPlaylistResponse(String session, String playlistId, String videoId, Boolean allowDuplicates) throws IOException, ExtractionException {

        if (StringUtils.isBlank(playlistId) || StringUtils.isBlank(videoId))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        var user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            return mapper.writeValueAsBytes(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Playlist.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Playlist.class);
            root.fetch("videos", JoinType.LEFT);
            root.fetch("owner", JoinType.LEFT);
            query.where(cb.equal(root.get("playlist_id"), UUID.fromString(playlistId)));
            var playlist = s.createQuery(query).uniqueResult();

            if (playlist == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            if (!allowDuplicates) {
                for (PlaylistVideo video : playlist.getVideos()) {
                    if (video.getId() == videoId)
                        return mapper.writeValueAsBytes(mapper.createObjectNode()
                                .put("error", "The playlist already contains the video"));
                }
                ;
            }

            if (playlist.getOwner().getId() != user.getId())
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "You are not the owner this playlist"));

            var video = DatabaseHelper.getPlaylistVideoFromId(s, videoId);

            if (video == null) {
                StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);

                String channelId = StringUtils.substringAfter(info.getUploaderUrl(), "/channel/");

                var channel = DatabaseHelper.getChannelFromId(s, channelId);

                if (channel == null) {
                    channel = saveChannel(channelId);
                }

                video = new PlaylistVideo(videoId, info.getName(), info.getThumbnailUrl(), info.getDuration(), channel);

                var tr = s.beginTransaction();
                s.persist(video);
                tr.commit();

            }

            if (playlist.getVideos().isEmpty())
                playlist.setThumbnail(video.getThumbnail());

            playlist.getVideos().add(video);

            var tr = s.beginTransaction();
            s.merge(playlist);
            tr.commit();

            return mapper.writeValueAsBytes(new AcceptedResponse());
        }
    }

    public static byte[] removeFromPlaylistResponse(String session, String playlistId, int index) throws IOException {

        if (StringUtils.isBlank(playlistId))
            return mapper.writeValueAsBytes(new InvalidRequestResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Playlist.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Playlist.class);
            root.fetch("videos", JoinType.LEFT);
            root.fetch("owner", JoinType.LEFT);
            query.where(cb.equal(root.get("playlist_id"), UUID.fromString(playlistId)));
            var playlist = s.createQuery(query).uniqueResult();

            if (playlist == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            if (playlist.getOwner().getId() != DatabaseHelper.getUserFromSession(session).getId())
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "You are not the owner this playlist"));

            if (index < 0 || index >= playlist.getVideos().size())
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Video Index out of bounds"));

            playlist.getVideos().remove(index);

            var tr = s.beginTransaction();
            s.merge(playlist);
            tr.commit();

            return mapper.writeValueAsBytes(new AcceptedResponse());
        }
    }

    public static final byte[] logoutResponse(String session) throws JsonProcessingException {

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var tr = s.beginTransaction();
            if (s.createMutationQuery("UPDATE User user SET user.sessionId = :newSessionId where user.sessionId = :sessionId")
                    .setParameter("sessionId", session).setParameter("newSessionId", String.valueOf(UUID.randomUUID()))
                    .executeUpdate() > 0) {
                tr.commit();
                return Constants.mapper.writeValueAsBytes(new AcceptedResponse());
            } else
                tr.rollback();
        }

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());
    }

    public static String registeredBadgeRedirect() {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            long registered = s.createQuery("select count(*) from User", Long.class).uniqueResult();

            return String.format("https://img.shields.io/badge/Registered%%20Users-%s-blue", registered);
        }
    }

    public static void handleNewVideo(String url, long time, me.kavin.piped.utils.obj.db.Channel channel) {
        try {
            handleNewVideo(StreamInfo.getInfo(url), time, channel);
        } catch (Exception e) {
            ExceptionHandler.handle(e);
        }
    }

    private static void handleNewVideo(StreamInfo info, long time, me.kavin.piped.utils.obj.db.Channel channel) {

        if (channel == null)
            channel = DatabaseHelper.getChannelFromId(
                    info.getUploaderUrl().substring("https://www.youtube.com/channel/".length()));

        long infoTime = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        Video video = null;

        if (channel != null && (video = DatabaseHelper.getVideoFromId(info.getId())) == null
                && (System.currentTimeMillis() - infoTime) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {

            video = new Video(info.getId(), info.getName(), info.getViewCount(), info.getDuration(),
                    Math.max(infoTime, time), info.getThumbnailUrl(), channel);

            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                var tr = s.beginTransaction();
                s.insert(video);
                tr.commit();
            }

        } else if (video != null) {
            updateVideo(info.getId(), info, time);
        }
    }

    private static void updateVideo(String id, StreamInfoItem item, long time, boolean addIfNotExistent) {
        Multithreading.runAsync(() -> {
            try {
                Video video = DatabaseHelper.getVideoFromId(id);

                if (video != null) {
                    try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                        updateVideo(s, video, item.getViewCount(), item.getDuration(), item.getName());
                    }
                } else if (addIfNotExistent) {
                    handleNewVideo("https://www.youtube.com/watch?v=" + id, time, null);
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    private static void updateVideo(String id, StreamInfo info, long time) {
        Multithreading.runAsync(() -> {
            try {
                Video video = DatabaseHelper.getVideoFromId(id);

                if (video != null) {
                    try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                        updateVideo(s, video, info.getViewCount(), info.getDuration(), info.getName());
                    }
                } else {
                    handleNewVideo(info, time, null);
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    private static void updateVideo(StatelessSession s, Video video, long views, long duration, String title) {

        boolean changed = false;

        if (duration > 0 && video.getDuration() != duration) {
            video.setDuration(duration);
            changed = true;
        }
        if (!video.getTitle().equals(title)) {
            video.setTitle(title);
            changed = true;
        }
        if (views > video.getViews()) {
            video.setViews(views);
            changed = true;
        }

        if (changed) {
            var tr = s.beginTransaction();
            s.update(video);
            tr.commit();
        }
    }

    private static me.kavin.piped.utils.obj.db.Channel saveChannel(String channelId) {

        if (!channelId.matches("[A-Za-z\\d_-]+"))
            return null;


        final ChannelInfo info;

        try {
            info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);
        } catch (IOException | ExtractionException e) {
            ExceptionUtils.rethrow(e);
            return null;
        }

        var channel = new me.kavin.piped.utils.obj.db.Channel(channelId, info.getName(),
                info.getAvatarUrl(), info.isVerified());

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var tr = s.beginTransaction();
            s.insert(channel);
            tr.commit();
        } catch (Exception e) {
            ExceptionHandler.handle(e);
        }

        Multithreading.runAsync(() -> {
            try {
                subscribePubSub(channelId);
            } catch (IOException e) {
                ExceptionHandler.handle(e);
            }
        });

        Multithreading.runAsync(() -> {
            for (StreamInfoItem item : info.getRelatedItems()) {
                long time = item.getUploadDate() != null
                        ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                        : System.currentTimeMillis();
                if ((System.currentTimeMillis() - time) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
                    handleNewVideo(item.getUrl(), time, channel);
            }
        });

        return channel;
    }

    public static void subscribePubSub(String channelId) throws IOException {

        PubSub pubsub = DatabaseHelper.getPubSubFromId(channelId);

        if (pubsub == null || System.currentTimeMillis() - pubsub.getSubbedAt() > TimeUnit.DAYS.toMillis(4)) {

            String callback = Constants.PUBSUB_URL + "/webhooks/pubsub";
            String topic = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId;

            var builder = new Request.Builder()
                    .url("https://pubsubhubbub.appspot.com/subscribe");

            var formBuilder = new FormBody.Builder();

            formBuilder.add("hub.callback", callback);
            formBuilder.add("hub.topic", topic);
            formBuilder.add("hub.verify", "async");
            formBuilder.add("hub.mode", "subscribe");
            formBuilder.add("hub.lease_seconds", "432000");

            var resp = Constants.h2client
                    .newCall(builder.post(formBuilder.build())
                            .build()).execute();

            if (resp.code() == 202) {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                    var tr = s.beginTransaction();
                    if (pubsub == null) {
                        pubsub = new PubSub(channelId, System.currentTimeMillis());
                        s.insert(pubsub);
                    } else {
                        pubsub.setSubbedAt(System.currentTimeMillis());
                        s.update(pubsub);
                    }
                    tr.commit();
                }

            } else
                System.out.println("Failed to subscribe: " + resp.code() + "\n" + Objects.requireNonNull(resp.body()).string());

            resp.close();
        }

    }

    private static StreamItem collectRelatedStream(Object o) {

        StreamInfoItem item = (StreamInfoItem) o;

        return new StreamItem(substringYouTube(item.getUrl()), item.getName(), rewriteURL(item.getThumbnailUrl()),
                item.getUploaderName(), substringYouTube(item.getUploaderUrl()),
                rewriteURL(item.getUploaderAvatarUrl()), item.getTextualUploadDate(), item.getShortDescription(), item.getDuration(),
                item.getViewCount(), item.getUploadDate() != null ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli() : -1, item.isUploaderVerified());
    }
}
