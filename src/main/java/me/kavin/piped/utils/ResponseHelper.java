package me.kavin.piped.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.feed.synd.SyndPersonImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.ipfs.IPFS;
import me.kavin.piped.utils.obj.Channel;
import me.kavin.piped.utils.obj.ChapterSegment;
import me.kavin.piped.utils.obj.Comment;
import me.kavin.piped.utils.obj.CommentsPage;
import me.kavin.piped.utils.obj.FeedItem;
import me.kavin.piped.utils.obj.PipedStream;
import me.kavin.piped.utils.obj.Playlist;
import me.kavin.piped.utils.obj.SearchResults;
import me.kavin.piped.utils.obj.StreamItem;
import me.kavin.piped.utils.obj.Streams;
import me.kavin.piped.utils.obj.StreamsPage;
import me.kavin.piped.utils.obj.Subtitle;
import me.kavin.piped.utils.obj.db.PubSub;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;
import me.kavin.piped.utils.obj.search.SearchChannel;
import me.kavin.piped.utils.obj.search.SearchItem;
import me.kavin.piped.utils.obj.search.SearchPlaylist;
import me.kavin.piped.utils.obj.search.SearchStream;
import me.kavin.piped.utils.resp.AcceptedResponse;
import me.kavin.piped.utils.resp.AlreadyRegisteredResponse;
import me.kavin.piped.utils.resp.AuthenticationFailureResponse;
import me.kavin.piped.utils.resp.IncorrectCredentialsResponse;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import me.kavin.piped.utils.resp.LoginResponse;
import me.kavin.piped.utils.resp.SubscribeStatusResponse;

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

        final StreamInfo info = futureStream.get();

//	System.out.println(Constants.mapper.writeValueAsString(info.getStreamSegments()));
        info.getSubtitles()
                .forEach(subtitle -> subtitles.add(new Subtitle(rewriteURL(subtitle.getUrl()),
                        subtitle.getFormat().getMimeType(), subtitle.getDisplayLanguageName(),
                        subtitle.getLanguageTag(), subtitle.isAutoGenerated())));

        final List<PipedStream> videoStreams = new ObjectArrayList<>();
        final List<PipedStream> audioStreams = new ObjectArrayList<>();

        final String lbryURL = futureLBRY.get();

        if (lbryURL != null)
            videoStreams.add(new PipedStream(lbryURL, "MP4", "LBRY", "video/mp4", false));

        final String hls;
        boolean livestream = false;

        if ((hls = info.getHlsUrl()) != null && !hls.isEmpty())
            livestream = true;

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

        info.getRelatedItems().forEach(o -> {
            StreamInfoItem item = (StreamInfoItem) o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        List<ChapterSegment> segments = new ObjectArrayList<>();

        info.getStreamSegments().forEach(
                segment -> segments.add(new ChapterSegment(segment.getTitle(), segment.getStartTimeSeconds())));

        long time = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        if (info.getUploadDate() != null && System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(10))
            updateViews(info.getId(), info.getViewCount(), time, false);

        final Streams streams = new Streams(info.getName(), info.getDescription().getContent(),
                info.getTextualUploadDate(), info.getUploaderName(), info.getUploaderUrl().substring(23),
                rewriteURL(info.getUploaderAvatarUrl()), rewriteURL(info.getThumbnailUrl()), info.getDuration(),
                info.getViewCount(), info.getLikeCount(), info.getDislikeCount(), audioStreams, videoStreams,
                relatedStreams, subtitles, livestream, hls);

        return Constants.mapper.writeValueAsBytes(streams);

    }

    public static final byte[] channelResponse(String channelPath) throws Exception {

        final ChannelInfo info = ChannelInfo.getInfo("https://youtube.com/" + channelPath);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getRelatedItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        Multithreading.runAsync(() -> {
            Session s = DatabaseSessionFactory.createSession();

            me.kavin.piped.utils.obj.db.Channel channel = DatabaseHelper.getChannelFromId(s, info.getId());

            if (channel != null) {
                for (StreamInfoItem item : info.getRelatedItems()) {
                    long time = item.getUploadDate() != null
                            ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                            : System.currentTimeMillis();
                    if (System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(10))
                        updateViews(item.getUrl().substring("https://www.youtube.com/watch?v=".length()),
                                item.getViewCount(), time, true);
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
                rewriteURL(info.getBannerUrl()), info.getDescription(), nextpage, relatedStreams);

        IPFS.publishData(channel);

        return Constants.mapper.writeValueAsBytes(channel);

    }

    public static final byte[] channelPageResponse(String channelId, String prevpageStr)
            throws IOException, ExtractionException, InterruptedException {

        Page prevpage = Constants.mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<StreamInfoItem> info = ChannelInfo.getMoreItems(Constants.YOUTUBE_SERVICE,
                "https://youtube.com/channel/" + channelId, prevpage);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, relatedStreams);

        return Constants.mapper.writeValueAsBytes(streamspage);

    }

    public static final byte[] trendingResponse(String region)
            throws ParsingException, ExtractionException, IOException {

        if (region == null)
            return Constants.mapper.writeValueAsBytes(new InvalidRequestResponse());

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        KioskList kioskList = Constants.YOUTUBE_SERVICE.getKioskList();
        kioskList.forceContentCountry(new ContentCountry(region));
        KioskExtractor<?> extractor = kioskList.getDefaultKioskExtractor();
        extractor.fetchPage();
        KioskInfo info = KioskInfo.getInfo(extractor);

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

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        final Playlist playlist = new Playlist(info.getName(), rewriteURL(info.getThumbnailUrl()),
                rewriteURL(info.getBannerUrl()), nextpage,
                info.getUploaderName().isEmpty() ? null : info.getUploaderName(),
                info.getUploaderUrl().isEmpty() ? null : info.getUploaderUrl().substring(23),
                rewriteURL(info.getUploaderAvatarUrl()), (int) info.getStreamCount(), relatedStreams);

        return Constants.mapper.writeValueAsBytes(playlist);

    }

    public static final byte[] playlistPageResponse(String playlistId, String prevpageStr)
            throws IOException, ExtractionException, InterruptedException {

        Page prevpage = Constants.mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<StreamInfoItem> info = PlaylistInfo.getMoreItems(Constants.YOUTUBE_SERVICE,
                "https://www.youtube.com/playlist?list=" + playlistId, prevpage);

        final List<StreamItem> relatedStreams = new ObjectArrayList<>();

        info.getItems().forEach(o -> {
            StreamInfoItem item = o;
            relatedStreams.add(new StreamItem(item.getUrl().substring(23), item.getName(),
                    rewriteURL(item.getThumbnailUrl()), item.getUploaderName(), item.getUploaderUrl().substring(23),
                    item.getTextualUploadDate(), item.getDuration(), item.getViewCount()));
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = Constants.mapper.writeValueAsString(page);
        }

        final StreamsPage streamspage = new StreamsPage(nextpage, relatedStreams);

        return Constants.mapper.writeValueAsBytes(streamspage);

    }

    public static final byte[] playlistRSSResponse(String playlistId)
            throws IOException, ExtractionException, InterruptedException, FeedException {

        final PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

        final List<SyndEntry> entries = new ObjectArrayList<>();

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle(info.getName());
        feed.setAuthor(info.getUploaderName());
        feed.setLink(info.getUrl());
        feed.setDescription(String.format("%s - Piped", info.getName()));

        info.getRelatedItems().forEach(o -> {
            StreamInfoItem item = o;
            SyndEntry entry = new SyndEntryImpl();
            entry.setAuthor(item.getUploaderName());
            entry.setUri(item.getUrl());
            entry.setTitle(item.getName());
            entries.add(entry);
        });

        feed.setEntries(entries);

        return new SyndFeedOutput().outputString(feed).getBytes(StandardCharsets.UTF_8);

    }

    public static final byte[] suggestionsResponse(String query)
            throws JsonProcessingException, IOException, ExtractionException {

        return Constants.mapper
                .writeValueAsBytes(Constants.YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query));

    }

    public static final byte[] searchResponse(String q, String filter)
            throws IOException, ExtractionException, InterruptedException {

        final SearchInfo info = SearchInfo.getInfo(Constants.YOUTUBE_SERVICE,
                Constants.YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q, Collections.singletonList(filter), null));

        ObjectArrayList<SearchItem> items = new ObjectArrayList<>();

        info.getRelatedItems().forEach(item -> {
            switch (item.getInfoType()) {
            case STREAM:
                StreamInfoItem stream = (StreamInfoItem) item;
                items.add(new SearchStream(item.getName(), rewriteURL(item.getThumbnailUrl()),
                        item.getUrl().substring(23), stream.getTextualUploadDate(), stream.getUploaderName(),
                        optionalSubstring(stream.getUploaderUrl(), 23), stream.getViewCount(), stream.getDuration(),
                        stream.isUploaderVerified()));
                break;
            case CHANNEL:
                ChannelInfoItem channel = (ChannelInfoItem) item;
                items.add(new SearchChannel(item.getName(), rewriteURL(item.getThumbnailUrl()),
                        item.getUrl().substring(23), channel.getDescription(), channel.getSubscriberCount(),
                        channel.getStreamCount(), channel.isVerified()));
                break;
            case PLAYLIST:
                PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                items.add(new SearchPlaylist(item.getName(), rewriteURL(item.getThumbnailUrl()),
                        item.getUrl().substring(23), playlist.getUploaderName(), playlist.getStreamCount()));
                break;
            default:
                break;
            }
        });

        Page nextpage = info.getNextPage();

        return Constants.mapper
                .writeValueAsBytes(new SearchResults(items, Constants.mapper.writeValueAsString(nextpage)));

    }

    public static final byte[] searchPageResponse(String q, String filter, String prevpageStr)
            throws IOException, ExtractionException, InterruptedException {

        Page prevpage = Constants.mapper.readValue(prevpageStr, Page.class);

        InfoItemsPage<InfoItem> pages = SearchInfo.getMoreItems(Constants.YOUTUBE_SERVICE,
                Constants.YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q, Collections.singletonList(filter), null),
                prevpage);

        ObjectArrayList<SearchItem> items = new ObjectArrayList<>();

        pages.getItems().forEach(item -> {
            switch (item.getInfoType()) {
            case STREAM:
                StreamInfoItem stream = (StreamInfoItem) item;
                items.add(new SearchStream(item.getName(), rewriteURL(item.getThumbnailUrl()),
                        item.getUrl().substring(23), stream.getTextualUploadDate(), stream.getUploaderName(),
                        optionalSubstring(stream.getUploaderUrl(), 23), stream.getViewCount(), stream.getDuration(),
                        stream.isUploaderVerified()));
                break;
            case CHANNEL:
                ChannelInfoItem channel = (ChannelInfoItem) item;
                items.add(new SearchChannel(item.getName(), rewriteURL(item.getThumbnailUrl()),
                        item.getUrl().substring(23), channel.getDescription(), channel.getSubscriberCount(),
                        channel.getStreamCount(), channel.isVerified()));
                break;
            case PLAYLIST:
                PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                items.add(new SearchPlaylist(item.getName(), rewriteURL(item.getThumbnailUrl()),
                        item.getUrl().substring(23), playlist.getUploaderName(), playlist.getStreamCount()));
                break;
            default:
                break;
            }
        });

        Page nextpage = pages.getNextPage();

        return Constants.mapper
                .writeValueAsBytes(new SearchResults(items, Constants.mapper.writeValueAsString(nextpage)));

    }

    public static final byte[] commentsResponse(String videoId) throws Exception {

        CommentsInfo info = commentsCache.get(videoId);

        List<Comment> comments = new ObjectArrayList<>();

        info.getRelatedItems().forEach(comment -> {
            comments.add(new Comment(comment.getUploaderName(), rewriteURL(comment.getUploaderAvatarUrl()),
                    comment.getCommentId(), comment.getCommentText(), comment.getTextualUploadDate(),
                    comment.getUploaderUrl().substring(19), comment.getLikeCount(), comment.isHeartedByUploader(),
                    comment.isPinned(), comment.isUploaderVerified()));
        });

        String nextpage = null;

        if (info.getNextPage() != null)
            nextpage = info.getNextPage().getUrl();

        CommentsPage commentsItem = new CommentsPage(comments, nextpage);

        return Constants.mapper.writeValueAsBytes(commentsItem);

    }

    public static final byte[] commentsPageResponse(String videoId, String url) throws Exception {

        CommentsInfo init = commentsCache.get(videoId);

        InfoItemsPage<CommentsInfoItem> info = CommentsInfo.getMoreItems(init, new Page(url));

        List<Comment> comments = new ObjectArrayList<>();

        info.getItems().forEach(comment -> {
            comments.add(new Comment(comment.getUploaderName(), rewriteURL(comment.getUploaderAvatarUrl()),
                    comment.getCommentId(), comment.getCommentText(), comment.getTextualUploadDate(),
                    comment.getUploaderUrl().substring(19), comment.getLikeCount(), comment.isHeartedByUploader(),
                    comment.isPinned(), comment.isUploaderVerified()));
        });

        String nextpage = null;

        if (info.getNextPage() != null)
            nextpage = info.getNextPage().getUrl();

        CommentsPage commentsItem = new CommentsPage(comments, nextpage);

        return Constants.mapper.writeValueAsBytes(commentsItem);

    }

    private static final Argon2PasswordEncoder argon2PasswordEncoder = new Argon2PasswordEncoder();

    public static final byte[] registerResponse(String user, String pass)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

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

        User newuser = new User(user, argon2PasswordEncoder.encode(pass), Collections.emptyList());

        s.save(newuser);
        s.getTransaction().begin();
        s.getTransaction().commit();

        s.close();

        return Constants.mapper.writeValueAsBytes(new LoginResponse(newuser.getSessionId()));

    }

    public static final byte[] loginResponse(String user, String pass)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        if (user == null || pass == null)
            return Constants.mapper.writeValueAsBytes(new InvalidRequestResponse());

        user = user.toLowerCase();

        Session s = DatabaseSessionFactory.createSession();
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<User> cr = cb.createQuery(User.class);
        Root<User> root = cr.from(User.class);
        cr.select(root).where(root.get("username").in(user));

        User dbuser = s.createQuery(cr).uniqueResult();

        if (dbuser != null && argon2PasswordEncoder.matches(pass, dbuser.getPassword())) {
            s.close();
            return Constants.mapper.writeValueAsBytes(new LoginResponse(dbuser.getSessionId()));
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new IncorrectCredentialsResponse());

    }

    public static final byte[] subscribeResponse(String session, String channelId)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

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
                                info.getAvatarUrl(), false);
                        sess.save(channel);
                        sess.beginTransaction().commit();

                        Multithreading.runAsync(() -> {
                            try {
                                Session sessSub = DatabaseSessionFactory.createSession();
                                subscribePubSub(channelId, sessSub);
                                sessSub.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                        for (StreamInfoItem item : info.getRelatedItems()) {
                            long time = item.getUploadDate() != null
                                    ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                    : System.currentTimeMillis();
                            if ((System.currentTimeMillis() - time) < TimeUnit.DAYS.toMillis(10))
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

    public static final byte[] unsubscribeResponse(String session, String channelId)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

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

    public static final byte[] isSubscribedResponse(String session, String channelId)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

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

    public static final byte[] feedResponse(String session)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSessionWithSubscribed(s, session);

        if (user != null) {

            List<FeedItem> feedItems = new ObjectArrayList<>();

            if (user.getSubscribed() != null && !user.getSubscribed().isEmpty()) {

                List<Video> videos = DatabaseHelper.getVideosFromChannelIds(s, user.getSubscribed());

                videos.forEach(video -> {
                    feedItems.add(new FeedItem("/watch?v=" + video.getId(), video.getTitle(),
                            rewriteURL(video.getThumbnail()), "/channel/" + video.getChannel().getUploaderId(),
                            video.getChannel().getUploader(), rewriteURL(video.getChannel().getUploaderAvatar()),
                            video.getViews(), video.getDuration(), video.getUploaded(),
                            video.getChannel().isVerified()));
                });

                Collections.sort(feedItems, (a, b) -> (int) (b.uploaded - a.uploaded));
            }

            s.close();

            return Constants.mapper.writeValueAsBytes(feedItems);
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static final byte[] feedResponseRSS(String session)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, FeedException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSessionWithSubscribed(s, session);

        if (user != null) {

            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("atom_1.0");
            feed.setTitle("Piped - Feed");
            feed.setDescription(String.format("Piped's RSS subscription feed for %s.", user.getUsername()));
            feed.setUri("https://piped.kavin.rocks/feed");

            if (user.getSubscribed() != null && !user.getSubscribed().isEmpty()) {

                List<Video> videos = DatabaseHelper.getVideosFromChannelIds(s, user.getSubscribed());

                Collections.sort(videos, (a, b) -> (int) (b.getUploaded() - a.getUploaded()));

                final List<SyndEntry> entries = new ObjectArrayList<>();

                for (Video video : videos) {
                    SyndEntry entry = new SyndEntryImpl();

                    SyndPerson person = new SyndPersonImpl();
                    person.setName(video.getChannel().getUploader());
                    person.setUri("https://piped.kavin.rocks/channel/" + video.getChannel().getUploaderId());

                    entry.setAuthors(Collections.singletonList(person));

                    entry.setUri("https://piped.kavin.rocks/watch?v=" + video.getId());
                    entry.setTitle(video.getTitle());
                    entry.setPublishedDate(new Date(video.getUploaded()));
                    entries.add(entry);
                }

                feed.setEntries(entries);

            }

            s.close();

            return new SyndFeedOutput().outputString(feed).getBytes(StandardCharsets.UTF_8);
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    public static final byte[] importResponse(String session, String[] channelIds)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        Session s = DatabaseSessionFactory.createSession();

        User user = DatabaseHelper.getUserFromSessionWithSubscribed(s, session);

        if (user != null) {

            Multithreading.runAsync(() -> {
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
                            ChannelInfo info = null;

                            try {
                                info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);
                            } catch (Exception e) {
                                return;
                            }

                            channel = new me.kavin.piped.utils.obj.db.Channel(channelId, info.getName(),
                                    info.getAvatarUrl(), false);
                            sess.save(channel);

                            Multithreading.runAsync(() -> {
                                try {
                                    Session sessSub = DatabaseSessionFactory.createSession();
                                    subscribePubSub(channelId, sessSub);
                                    sessSub.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });

                            for (StreamInfoItem item : info.getRelatedItems()) {
                                long time = item.getUploadDate() != null
                                        ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                        : System.currentTimeMillis();
                                if ((System.currentTimeMillis() - time) < TimeUnit.DAYS.toMillis(10))
                                    handleNewVideo(item.getUrl(), time, channel, sess);
                            }

                            if (!sess.getTransaction().isActive())
                                sess.getTransaction().begin();
                            sess.getTransaction().commit();
                        }

                        sess.close();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                });

            }

            return Constants.mapper.writeValueAsBytes(new AcceptedResponse());
        }

        s.close();

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());

    }

    private static final String getLBRYStreamURL(String videoId) throws IOException, InterruptedException {

        String lbryId = new JSONObject(Constants.h2client.send(HttpRequest
                .newBuilder(URI.create("https://api.lbry.com/yt/resolve?video_ids=" + URLUtils.silentEncode(videoId)))
                .setHeader("User-Agent", Constants.USER_AGENT).build(), BodyHandlers.ofString()).body())
                        .getJSONObject("data").getJSONObject("videos").optString(videoId);

        if (!lbryId.isEmpty())
            new JSONObject(Constants.h2client.send(HttpRequest
                    .newBuilder(URI.create("https://api.lbry.tv/api/v1/proxy?m=get"))
                    .POST(BodyPublishers.ofString(
                            String.valueOf(new JSONObject().put("jsonrpc", "2.0").put("method", "get").put("params",
                                    new JSONObject().put("uri", "lbry://" + lbryId).put("save_file", true)))))
                    .build(), BodyHandlers.ofString()).body()).getJSONObject("result").getString("streaming_url");

        return null;

    }

    public static void handleNewVideo(String url, long time, me.kavin.piped.utils.obj.db.Channel channel, Session s) {
        try {
            handleNewVideo(StreamInfo.getInfo(url), time, channel, s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleNewVideo(StreamInfo info, long time, me.kavin.piped.utils.obj.db.Channel channel,
            Session s) {

        if (channel == null)
            channel = DatabaseHelper.getChannelFromId(s,
                    info.getUploaderUrl().substring("https://www.youtube.com/channel/".length()));

        long infoTime = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        Video video = null;

        if (channel != null && (video = DatabaseHelper.getVideoFromId(s, info.getId())) == null
                && (System.currentTimeMillis() - infoTime) < TimeUnit.DAYS.toMillis(10)) {

            video = new Video(info.getId(), info.getName(), info.getViewCount(), info.getDuration(),
                    Math.max(infoTime, time), info.getThumbnailUrl(), channel);

            s.save(video);

            if (!s.getTransaction().isActive())
                s.getTransaction().begin();
            s.getTransaction().commit();
        } else if (video != null) {
            video.setViews(info.getViewCount());

            s.update(video);

            if (!s.getTransaction().isActive())
                s.getTransaction().begin();
            s.getTransaction().commit();
        }

    }

    private static void updateViews(String id, long views, long time, boolean addIfNonExistent) {
        Multithreading.runAsync(() -> {
            try {
                Session s = DatabaseSessionFactory.createSession();

                Video video = DatabaseHelper.getVideoFromId(s, id);

                if (video != null) {
                    video.setViews(views);
                    s.update(video);
                    s.beginTransaction().commit();
                } else if (addIfNonExistent)
                    handleNewVideo("https://www.youtube.com/watch?v=" + id, time, null, s);

                s.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void subscribePubSub(String channelId, Session s) throws IOException, InterruptedException {

        PubSub pubsub = DatabaseHelper.getPubSubFromId(s, channelId);

        if (pubsub == null || System.currentTimeMillis() - pubsub.getSubbedAt() > TimeUnit.DAYS.toMillis(4)) {
            System.out.println(String.format("PubSub: Subscribing to %s", channelId));

            String callback = Constants.PUBLIC_URL + "/webhooks/pubsub";
            String topic = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId;

            Builder builder = HttpRequest.newBuilder(URI.create("https://pubsubhubbub.appspot.com/subscribe"));

            Map<String, String> formParams = new Object2ObjectOpenHashMap<>();
            StringBuilder formBody = new StringBuilder();

            builder.header("content-type", "application/x-www-form-urlencoded");

            formParams.put("hub.callback", callback);
            formParams.put("hub.topic", topic);
            formParams.put("hub.verify", "async");
            formParams.put("hub.mode", "subscribe");
            formParams.put("hub.lease_seconds", "432000");

            formParams.forEach((name, value) -> {
                formBody.append(name + "=" + URLUtils.silentEncode(value) + "&");
            });

            builder.method("POST",
                    BodyPublishers.ofString(String.valueOf(formBody.substring(0, formBody.length() - 1))));

            HttpResponse<InputStream> resp = Constants.h2client.send(builder.build(), BodyHandlers.ofInputStream());

            if (resp.statusCode() == 204) {
                if (pubsub == null)
                    pubsub = new PubSub(channelId, System.currentTimeMillis());
                else
                    pubsub.setSubbedAt(System.currentTimeMillis());

                s.saveOrUpdate(pubsub);

                if (!s.getTransaction().isActive())
                    s.getTransaction().begin();
                s.getTransaction().commit();
            } else
                System.out.println(
                        "Failed to subscribe: " + resp.statusCode() + "\n" + IOUtils.toString(resp.body(), "UTF-8"));
        }

    }

    private static final String optionalSubstring(String s, int index) {
        return s == null || s.isEmpty() ? null : s.substring(index);
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

        return Constants.PROXY_PART + path + (hasQuery ? "?" + query + "&host=" : "?host=")
                + URLUtils.silentEncode(host);

    }
}
