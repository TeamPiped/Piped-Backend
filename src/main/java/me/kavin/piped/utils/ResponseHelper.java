package me.kavin.piped.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.ipfs.IPFS;
import me.kavin.piped.utils.obj.*;
import me.kavin.piped.utils.obj.db.PubSub;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;
import me.kavin.piped.utils.obj.search.SearchChannel;
import me.kavin.piped.utils.obj.search.SearchPlaylist;
import me.kavin.piped.utils.resp.*;
import okhttp3.FormBody;
import okhttp3.Request;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.utils.URLUtils.rewriteURL;
import static me.kavin.piped.utils.URLUtils.substringYouTube;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredContentCountry;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredLocalization;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;

public class ResponseHelper {

    public static final LoadingCache<String, CommentsInfo> commentsCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .scheduler(Scheduler.systemScheduler())
            .maximumSize(1000).build(key -> CommentsInfo.getInfo("https://www.youtube.com/watch?v=" + key));

    public static byte[] streamsResponse(String videoId) throws Exception {

        CompletableFuture<StreamInfo> futureStream = CompletableFuture.supplyAsync(() -> {
            try {
                return StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);
            } catch (Exception e) {
                ExceptionUtils.rethrow(e);
            }
            return null;
        }, Multithreading.getCachedExecutor());

        CompletableFuture<String> futureLbryId = CompletableFuture.supplyAsync(() -> {
            try {
                return LbryHelper.getLBRYId(videoId);
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
            return null;
        }, Multithreading.getCachedExecutor());

        CompletableFuture<String> futureLBRY = CompletableFuture.supplyAsync(() -> {
            try {
                String lbryId = futureLbryId.completeOnTimeout(null, 2, TimeUnit.SECONDS).get();

                return LbryHelper.getLBRYStreamURL(lbryId);
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
            return null;
        }, Multithreading.getCachedExecutor());

        final List<Subtitle> subtitles = new ObjectArrayList<>();
        final List<ChapterSegment> chapters = new ObjectArrayList<>();

        final StreamInfo info = futureStream.get();

        info.getStreamSegments().forEach(segment -> chapters.add(new ChapterSegment(segment.getTitle(), rewriteURL(segment.getPreviewUrl()),
                segment.getStartTimeSeconds())));

        info.getSubtitles()
                .forEach(subtitle -> subtitles.add(new Subtitle(rewriteURL(subtitle.getUrl()),
                        subtitle.getFormat().getMimeType(), subtitle.getDisplayLanguageName(),
                        subtitle.getLanguageTag(), subtitle.isAutoGenerated())));

        final List<PipedStream> videoStreams = new ObjectArrayList<>();
        final List<PipedStream> audioStreams = new ObjectArrayList<>();

        String lbryURL = null;

        try {
            lbryURL = futureLBRY.completeOnTimeout(null, 3, TimeUnit.SECONDS).get();
        } catch (Exception e) {
            // ignored
        }

        if (lbryURL != null)
            videoStreams.add(new PipedStream(lbryURL, "MP4", "LBRY", "video/mp4", false));

        boolean livestream = info.getStreamType() == StreamType.LIVE_STREAM;

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

        info.getRelatedItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        long time = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        if (info.getUploadDate() != null && System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
            updateVideo(info.getId(), info, time);

        final Streams streams = new Streams(info.getName(), info.getDescription().getContent(),
                info.getTextualUploadDate(), info.getUploaderName(), substringYouTube(info.getUploaderUrl()),
                rewriteURL(info.getUploaderAvatarUrl()), rewriteURL(info.getThumbnailUrl()), info.getDuration(),
                info.getViewCount(), info.getLikeCount(), info.getDislikeCount(), info.isUploaderVerified(),
                audioStreams, videoStreams, relatedStreams, subtitles, livestream, rewriteURL(info.getHlsUrl()),
                rewriteURL(info.getDashMpdUrl()), futureLbryId.get(), chapters);

        return Constants.mapper.writeValueAsBytes(streams);

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

        return Constants.mapper.writeValueAsBytes(new VideoResolvedResponse(videoId));
    }

    public static byte[] channelResponse(String channelPath) throws Exception {

        final ChannelInfo info = ChannelInfo.getInfo("https://youtube.com/" + channelPath);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        Multithreading.runAsync(() -> {
            Session s = DatabaseSessionFactory.createSession();

            me.kavin.piped.utils.obj.db.Channel channel = DatabaseHelper.getChannelFromId(s, info.getId());

            if (channel != null) {
                if (channel.isVerified() != info.isVerified()
                        || !channel.getUploaderAvatar().equals(info.getAvatarUrl())) {
                    channel.setVerified(info.isVerified());
                    channel.setUploaderAvatar(info.getAvatarUrl());
                    if (!s.getTransaction().isActive())
                        s.getTransaction().begin();
                    s.update(channel);
                    s.getTransaction().commit();
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

            s.close();
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        final Channel channel = new Channel(info.getId(), info.getName(), rewriteURL(info.getAvatarUrl()),
                rewriteURL(info.getBannerUrl()), info.getDescription(), info.getSubscriberCount(), info.isVerified(),
                nextpage, relatedStreams);

        IPFS.publishData(channel);

        return Constants.mapper.writeValueAsBytes(channel);

    }

    public static byte[] channelPageResponse(String channelId, String prevpageStr)
            throws IOException, ExtractionException {

        Page prevpage = Constants.mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<StreamInfoItem> info = ChannelInfo.getMoreItems(YOUTUBE_SERVICE,
                "https://youtube.com/channel/" + channelId, prevpage);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, relatedStreams);

        return Constants.mapper.writeValueAsBytes(streamspage);

    }

    public static byte[] trendingResponse(String region)
            throws ExtractionException, IOException {

        if (region == null)
            return Constants.mapper.writeValueAsBytes(new InvalidRequestResponse());

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        KioskList kioskList = YOUTUBE_SERVICE.getKioskList();
        kioskList.forceContentCountry(new ContentCountry(region));
        KioskExtractor<?> extractor = kioskList.getDefaultKioskExtractor();
        extractor.fetchPage();
        KioskInfo info = KioskInfo.getInfo(extractor);

        info.getRelatedItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        return Constants.mapper.writeValueAsBytes(relatedStreams);
    }

    public static byte[] playlistResponse(String playlistId)
            throws IOException, ExtractionException {

        final PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        final Playlist playlist = new Playlist(info.getName(), rewriteURL(info.getThumbnailUrl()),
                rewriteURL(info.getBannerUrl()), nextpage,
                info.getUploaderName().isEmpty() ? null : info.getUploaderName(),
                substringYouTube(info.getUploaderUrl()), rewriteURL(info.getUploaderAvatarUrl()),
                (int) info.getStreamCount(), relatedStreams);

        return Constants.mapper.writeValueAsBytes(playlist);

    }

    public static byte[] playlistPageResponse(String playlistId, String prevpageStr)
            throws IOException, ExtractionException {

        Page prevpage = Constants.mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<StreamInfoItem> info = PlaylistInfo.getMoreItems(YOUTUBE_SERVICE,
                "https://www.youtube.com/playlist?list=" + playlistId, prevpage);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> relatedStreams.add(collectRelatedStream(o)));

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, relatedStreams);

        return Constants.mapper.writeValueAsBytes(streamspage);

    }

    public static byte[] playlistRSSResponse(String playlistId)
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

        return Constants.mapper.writeValueAsBytes(YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query));

    }

    public static byte[] opensearchSuggestionsResponse(String query)
            throws IOException, ExtractionException {

        return Constants.mapper.writeValueAsBytes(Arrays.asList(
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
                case STREAM:
                    items.add(collectRelatedStream(item));
                    break;
                case CHANNEL:
                    ChannelInfoItem channel = (ChannelInfoItem) item;
                    items.add(new SearchChannel(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), channel.getDescription(), channel.getSubscriberCount(),
                            channel.getStreamCount(), channel.isVerified()));
                    break;
                case PLAYLIST:
                    PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                    items.add(new SearchPlaylist(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), playlist.getUploaderName(), playlist.getStreamCount()));
                    break;
                default:
                    break;
            }
        });

        Page nextpage = info.getNextPage();

        return Constants.mapper.writeValueAsBytes(new SearchResults(items,
                Constants.mapper.writeValueAsString(nextpage), info.getSearchSuggestion(), info.isCorrectedSearch()));

    }

    public static byte[] searchPageResponse(String q, String filter, String prevpageStr)
            throws IOException, ExtractionException {

        Page prevpage = Constants.mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<InfoItem> pages = SearchInfo.getMoreItems(YOUTUBE_SERVICE,
                YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q, Collections.singletonList(filter), null), prevpage);

        ObjectArrayList<Object> items = new ObjectArrayList<>();

        pages.getItems().forEach(item -> {
            switch (item.getInfoType()) {
                case STREAM:
                    items.add(collectRelatedStream(item));
                    break;
                case CHANNEL:
                    ChannelInfoItem channel = (ChannelInfoItem) item;
                    items.add(new SearchChannel(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), channel.getDescription(), channel.getSubscriberCount(),
                            channel.getStreamCount(), channel.isVerified()));
                    break;
                case PLAYLIST:
                    PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                    items.add(new SearchPlaylist(item.getName(), rewriteURL(item.getThumbnailUrl()),
                            substringYouTube(item.getUrl()), playlist.getUploaderName(), playlist.getStreamCount()));
                    break;
                default:
                    break;
            }
        });

        Page nextpage = pages.getNextPage();

        return Constants.mapper
                .writeValueAsBytes(new SearchResults(items, Constants.mapper.writeValueAsString(nextpage)));

    }

    public static byte[] commentsResponse(String videoId) throws Exception {

        CommentsInfo info = commentsCache.get(videoId);

        List<Comment> comments = new ObjectArrayList<>();

        info.getRelatedItems().forEach(comment -> {
            try {
                String repliespage = null;
                if (comment.getReplies() != null)
                    repliespage = Constants.mapper.writeValueAsString(comment.getReplies());

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
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        CommentsPage commentsItem = new CommentsPage(comments, nextpage, info.isCommentsDisabled());

        return Constants.mapper.writeValueAsBytes(commentsItem);

    }

    public static byte[] commentsPageResponse(String videoId, String prevpageStr) throws Exception {

        Page prevpage = Constants.mapper.readValue(prevpageStr, Page.class);

        CommentsInfo init = commentsCache.get(videoId);

        InfoItemsPage<CommentsInfoItem> info = CommentsInfo.getMoreItems(init, prevpage);

        List<Comment> comments = new ObjectArrayList<>();

        info.getItems().forEach(comment -> {
            try {
                String repliespage = null;
                if (comment.getReplies() != null)
                    repliespage = Constants.mapper.writeValueAsString(comment.getReplies());

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
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        CommentsPage commentsItem = new CommentsPage(comments, nextpage, init.isCommentsDisabled());

        return Constants.mapper.writeValueAsBytes(commentsItem);

    }

    private static final Argon2PasswordEncoder argon2PasswordEncoder = new Argon2PasswordEncoder();

    public static byte[] registerResponse(String user, String pass) throws IOException {

        if (Constants.DISABLE_REGISTRATION)
            return Constants.mapper.writeValueAsBytes(new DisabledRegistrationResponse());

        if (user == null || pass == null)
            return Constants.mapper.writeValueAsBytes(new InvalidRequestResponse());

        user = user.toLowerCase();

        Session s = DatabaseSessionFactory.createSession();
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<User> cr = cb.createQuery(User.class);
        Root<User> root = cr.from(User.class);
        cr.select(root).where(root.get("username").in(user));
        boolean registered = s.createQuery(cr).uniqueResult() != null;

        if (registered) {
            s.close();
            return Constants.mapper.writeValueAsBytes(new AlreadyRegisteredResponse());
        }

        if (Constants.COMPROMISED_PASSWORD_CHECK) {
            String sha1Hash = DigestUtils.sha1Hex(pass).toUpperCase();
            String prefix = sha1Hash.substring(0, 5);
            String suffix = sha1Hash.substring(5);
            String[] entries = RequestUtils
                    .sendGet("https://api.pwnedpasswords.com/range/" + prefix, "github.com/TeamPiped/Piped-Backend")
                    .split("\n");
            for (String entry : entries)
                if (StringUtils.substringBefore(entry, ":").equals(suffix))
                    return Constants.mapper.writeValueAsBytes(new CompromisedPasswordResponse());
        }

        User newuser = new User(user, argon2PasswordEncoder.encode(pass), Collections.emptyList());

        s.save(newuser);
        s.getTransaction().begin();
        s.getTransaction().commit();

        s.close();

        return Constants.mapper.writeValueAsBytes(new LoginResponse(newuser.getSessionId()));

    }

    private static final BCryptPasswordEncoder bcryptPasswordEncoder = new BCryptPasswordEncoder();

    public static byte[] loginResponse(String user, String pass)
            throws IOException {

        if (user == null || pass == null)
            return Constants.mapper.writeValueAsBytes(new InvalidRequestResponse());

        user = user.toLowerCase();

        Session s = DatabaseSessionFactory.createSession();
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<User> cr = cb.createQuery(User.class);
        Root<User> root = cr.from(User.class);
        cr.select(root).where(root.get("username").in(user));

        User dbuser = s.createQuery(cr).uniqueResult();

        if (dbuser != null) {
            String hash = dbuser.getPassword();
            if (hash.startsWith("$argon2")) {
                if (argon2PasswordEncoder.matches(pass, hash)) {
                    s.close();
                    return Constants.mapper.writeValueAsBytes(new LoginResponse(dbuser.getSessionId()));
                }
            } else if (bcryptPasswordEncoder.matches(pass, hash)) {
                s.close();
                return Constants.mapper.writeValueAsBytes(new LoginResponse(dbuser.getSessionId()));
            }
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new IncorrectCredentialsResponse());

    }

    public static byte[] subscribeResponse(String session, String channelId)
            throws IOException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSessionWithSubscribed(s, session);

        if (user != null) {
            if (!user.getSubscribed().contains(channelId)) {

                s.getTransaction().begin();
                s.createNativeQuery("insert into users_subscribed (subscriber, channel) values (?,?)")
                        .setParameter(1, user.getId()).setParameter(2, channelId).executeUpdate();
                s.getTransaction().commit();
                s.close();

                Multithreading.runAsync(() -> {
                    Session sess = DatabaseSessionFactory.createSession();
                    me.kavin.piped.utils.obj.db.Channel channel = DatabaseHelper.getChannelFromId(sess, channelId);

                    if (channel == null) {
                        ChannelInfo info = null;

                        try {
                            info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);
                        } catch (IOException | ExtractionException e) {
                            ExceptionUtils.rethrow(e);
                        }

                        channel = new me.kavin.piped.utils.obj.db.Channel(channelId, info.getName(),
                                info.getAvatarUrl(), info.isVerified());
                        sess.save(channel);
                        sess.beginTransaction().commit();

                        Multithreading.runAsync(() -> {
                            try {
                                Session sessSub = DatabaseSessionFactory.createSession();
                                subscribePubSub(channelId, sessSub);
                                sessSub.close();
                            } catch (Exception e) {
                                ExceptionHandler.handle(e);
                            }
                        });

                        for (StreamInfoItem item : info.getRelatedItems()) {
                            long time = item.getUploadDate() != null
                                    ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                    : System.currentTimeMillis();
                            if ((System.currentTimeMillis() - time) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
                                handleNewVideo(item.getUrl(), time, channel, sess);
                        }
                    }

                    sess.close();
                });
            }

            return Constants.mapper.writeValueAsBytes(new AcceptedResponse());
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] unsubscribeResponse(String session, String channelId)
            throws IOException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSession(s, session);

        if (user != null) {
            s.getTransaction().begin();
            s.createNativeQuery("delete from users_subscribed where subscriber = :id and channel = :channel")
                    .setParameter("id", user.getId()).setParameter("channel", channelId).executeUpdate();
            s.getTransaction().commit();
            s.close();
            return Constants.mapper.writeValueAsBytes(new AcceptedResponse());
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] isSubscribedResponse(String session, String channelId)
            throws IOException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSessionWithSubscribed(s, session);

        if (user != null) {
            if (user.getSubscribed().contains(channelId)) {
                s.close();
                return Constants.mapper.writeValueAsBytes(new SubscribeStatusResponse(true));
            }
            s.close();
            return Constants.mapper.writeValueAsBytes(new SubscribeStatusResponse(false));
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] feedResponse(String session)
            throws IOException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSession(s, session);

        if (user != null) {

            CriteriaBuilder cb = s.getCriteriaBuilder();

            // Get all videos from subscribed channels, with channel info
            CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
            criteria.distinct(true);
            var root = criteria.from(Video.class);
            var userRoot = criteria.from(User.class);
            root.fetch("channel", JoinType.INNER);

            criteria.select(root)
                    .where(cb.and(
                            cb.isMember(root.get("channel"), userRoot.<Collection<String>>get("subscribed_ids")),
                            cb.equal(userRoot.get("id"), user.getId())
                    ))
                    .orderBy(cb.desc(root.get("uploaded")));

            List<StreamItem> feedItems = new ObjectArrayList<>();

            for (Video video : s.createQuery(criteria).list()) {
                var channel = video.getChannel();

                feedItems.add(new StreamItem("/watch?v=" + video.getId(), video.getTitle(),
                        rewriteURL(video.getThumbnail()), channel.getUploader(), "/channel/" + channel.getUploaderId(),
                        rewriteURL(channel.getUploaderAvatar()), null, null, video.getDuration(), video.getViews(),
                        video.getUploaded(), channel.isVerified()));
            }

            feedItems.sort(Comparator.<StreamItem>comparingLong(o -> o.uploaded).reversed());

            s.close();

            return Constants.mapper.writeValueAsBytes(feedItems);
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] feedResponseRSS(String session)
            throws IOException, FeedException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSession(s, session);

        if (user != null) {

            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("atom_1.0");
            feed.setTitle("Piped - Feed");
            feed.setDescription(String.format("Piped's RSS subscription feed for %s.", user.getUsername()));
            feed.setUri(Constants.FRONTEND_URL + "/feed");
            feed.setPublishedDate(new Date());

            CriteriaBuilder cb = s.getCriteriaBuilder();

            // Get all videos from subscribed channels, with channel info
            CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
            criteria.distinct(true);
            var root = criteria.from(Video.class);
            var userRoot = criteria.from(User.class);
            root.fetch("channel", JoinType.INNER);

            criteria.select(root)
                    .where(cb.and(
                            cb.isMember(root.get("channel"), userRoot.<Collection<String>>get("subscribed_ids")),
                            cb.equal(userRoot.get("id"), user.getId())
                    ))
                    .orderBy(cb.desc(root.get("uploaded")));

            List<Video> videos = s.createQuery(criteria).list();

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


            s.close();

            return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] importResponse(String session, String[] channelIds, boolean override)
            throws IOException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSessionWithSubscribed(s, session);

        if (user != null) {

            Multithreading.runAsync(() -> {
                if (override)
                    user.setSubscribed(Arrays.asList(channelIds));
                else
                    for (String channelId : channelIds)
                        if (!user.getSubscribed().contains(channelId))
                            user.getSubscribed().add(channelId);

                if (channelIds.length > 0) {
                    s.update(user);
                    s.beginTransaction().commit();
                }

                s.close();
            });

            for (String channelId : channelIds) {

                Multithreading.runAsyncLimited(() -> {
                    try {

                        Session sess = DatabaseSessionFactory.createSession();

                        me.kavin.piped.utils.obj.db.Channel channel = DatabaseHelper.getChannelFromId(sess, channelId);

                        if (channel == null) {
                            ChannelInfo info;

                            try {
                                info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);
                            } catch (Exception e) {
                                return;
                            }

                            channel = new me.kavin.piped.utils.obj.db.Channel(channelId, info.getName(),
                                    info.getAvatarUrl(), info.isVerified());
                            sess.save(channel);

                            Multithreading.runAsync(() -> {
                                try {
                                    Session sessSub = DatabaseSessionFactory.createSession();
                                    subscribePubSub(channelId, sessSub);
                                    sessSub.close();
                                } catch (Exception e) {
                                    ExceptionHandler.handle(e);
                                }
                            });

                            for (StreamInfoItem item : info.getRelatedItems()) {
                                long time = item.getUploadDate() != null
                                        ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                        : System.currentTimeMillis();
                                if ((System.currentTimeMillis() - time) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
                                    handleNewVideo(item.getUrl(), time, channel, sess);
                            }

                            if (!sess.getTransaction().isActive())
                                sess.getTransaction().begin();
                            sess.getTransaction().commit();
                        }

                        sess.close();

                    } catch (Exception e) {
                        ExceptionHandler.handle(e);
                    }

                });

            }

            return Constants.mapper.writeValueAsBytes(new AcceptedResponse());
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static byte[] subscriptionsResponse(String session)
            throws IOException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSession(s, session);

        if (user != null) {

            List<SubscriptionChannel> subscriptionItems = new ObjectArrayList<>();

            CriteriaBuilder cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Channel.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Channel.class);
            var userRoot = query.from(User.class);
            query.select(root);
            query.where(cb.and(
                    cb.isMember(root.get("uploader_id"), userRoot.<Collection<String>>get("subscribed_ids")),
                    cb.equal(userRoot.get("id"), user.getId())
            ));

            var channels = s.createQuery(query).list();

            channels.forEach(channel -> subscriptionItems.add(new SubscriptionChannel("/channel/" + channel.getUploaderId(),
                    channel.getUploader(), rewriteURL(channel.getUploaderAvatar()), channel.isVerified())));

            subscriptionItems.sort(Comparator.comparing(o -> o.name));

            s.close();

            return Constants.mapper.writeValueAsBytes(subscriptionItems);
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static String registeredBadgeRedirect() {

        Session s = DatabaseSessionFactory.createSession();

        long registered = (Long) s.createQuery("select count(*) from User").uniqueResult();

        s.close();

        return String.format("https://img.shields.io/badge/Registered%%20Users-%s-blue", registered);
    }

    public static void handleNewVideo(String url, long time, me.kavin.piped.utils.obj.db.Channel channel, Session s) {
        try {
            handleNewVideo(StreamInfo.getInfo(url), time, channel, s);
        } catch (Exception e) {
            ExceptionHandler.handle(e);
        }
    }

    private static void handleNewVideo(StreamInfo info, long time, me.kavin.piped.utils.obj.db.Channel channel, Session s) {

        if (channel == null)
            channel = DatabaseHelper.getChannelFromId(s,
                    info.getUploaderUrl().substring("https://www.youtube.com/channel/".length()));

        long infoTime = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        Video video = null;

        if (channel != null && (video = DatabaseHelper.getVideoFromId(s, info.getId())) == null
                && (System.currentTimeMillis() - infoTime) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {

            video = new Video(info.getId(), info.getName(), info.getViewCount(), info.getDuration(),
                    Math.max(infoTime, time), info.getThumbnailUrl(), channel);

            s.save(video);

            if (!s.getTransaction().isActive())
                s.getTransaction().begin();
            s.getTransaction().commit();
        } else if (video != null) {
            updateVideo(info.getId(), info, time);
        }
    }

    private static void updateVideo(String id, StreamInfoItem item, long time, boolean addIfNotExistent) {
        Multithreading.runAsync(() -> {
            try {
                Session s = DatabaseSessionFactory.createSession();
                Video video = DatabaseHelper.getVideoFromId(s, id);

                if (video != null) {
                    updateVideo(s, video, item.getViewCount(), item.getDuration(), item.getName());
                } else if (addIfNotExistent) {
                    handleNewVideo("https://www.youtube.com/watch?v=" + id, time, null, s);
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    private static void updateVideo(String id, StreamInfo info, long time) {
        Multithreading.runAsync(() -> {
            try {
                Session s = DatabaseSessionFactory.createSession();
                Video video = DatabaseHelper.getVideoFromId(s, id);

                if (video != null) {
                    updateVideo(s, video, info.getViewCount(), info.getDuration(), info.getName());
                } else {
                    handleNewVideo(info, time, null, s);
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    private static void updateVideo(Session s, Video video, long views, long duration, String title) {

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
            s.update(video);
            if (!s.getTransaction().isActive()) s.getTransaction().begin();
            s.getTransaction().commit();
        }
    }

    public static void subscribePubSub(String channelId, Session s) throws IOException {

        PubSub pubsub = DatabaseHelper.getPubSubFromId(s, channelId);

        if (pubsub == null || System.currentTimeMillis() - pubsub.getSubbedAt() > TimeUnit.DAYS.toMillis(4)) {

            String callback = Constants.PUBLIC_URL + "/webhooks/pubsub";
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
                if (pubsub == null)
                    pubsub = new PubSub(channelId, System.currentTimeMillis());
                else
                    pubsub.setSubbedAt(System.currentTimeMillis());

                s.saveOrUpdate(pubsub);

                if (!s.getTransaction().isActive())
                    s.getTransaction().begin();
                s.getTransaction().commit();
            } else
                System.out.println("Failed to subscribe: " + resp.code() + "\n" + Objects.requireNonNull(resp.body()).string());
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
