package me.kavin.piped.server.handlers.auth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.ContentItem;
import me.kavin.piped.utils.obj.Playlist;
import me.kavin.piped.utils.obj.StreamItem;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.PlaylistVideo;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.resp.AcceptedResponse;
import me.kavin.piped.utils.resp.AuthenticationFailureResponse;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.URLUtils.rewriteURL;
import static me.kavin.piped.utils.URLUtils.substringYouTube;

public class AuthPlaylistHandlers {
    public static byte[] playlistPipedResponse(String playlistId) throws Exception {

        if (StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("playlistId is a required parameter"));

        try (Session s = DatabaseSessionFactory.createSession()) {

            var playlistCompletableFuture = Multithreading.supplyAsync(() -> DatabaseHelper.getPlaylistFromId(s, playlistId));
            var playlistVideosCompletableFuture = Multithreading.supplyAsync(() -> DatabaseHelper.getPlaylistVideosFromPlaylistId(playlistId, true));

            var pl = playlistCompletableFuture.get();

            if (pl == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            final List<ContentItem> relatedStreams = new ObjectArrayList<>();

            var videos = playlistVideosCompletableFuture.get();

            for (var video : videos) {
                var channel = video.getChannel();
                relatedStreams.add(new StreamItem("/watch?v=" + video.getId(), video.getTitle(), rewriteURL(video.getThumbnail()), channel.getUploader(),
                        "/channel/" + channel.getUploaderId(), rewriteURL(channel.getUploaderAvatar()), null, null,
                        video.getDuration(), -1, -1, channel.isVerified(), false));
            }

            final Playlist playlist = new Playlist(pl.getName(), rewriteURL(pl.getThumbnail()), pl.getShortDescription(), null, null, pl.getOwner().getUsername(),
                    null, null, videos.size(), relatedStreams);

            return mapper.writeValueAsBytes(playlist);
        }
    }

    public static byte[] playlistPipedRSSResponse(String playlistId)
            throws Exception {

        if (StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("playlistId is required parameter"));

        try (Session s = DatabaseSessionFactory.createSession()) {
            var playlistCompletableFuture = Multithreading.supplyAsync(() -> DatabaseHelper.getPlaylistFromId(s, playlistId));
            var playlistVideosCompletableFuture = Multithreading.supplyAsync(() -> DatabaseHelper.getPlaylistVideosFromPlaylistId(playlistId, true));

            var pl = playlistCompletableFuture.get();

            final List<SyndEntry> entries = new ObjectArrayList<>();

            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("rss_2.0");
            feed.setTitle(pl.getName());
            feed.setAuthor(pl.getOwner().getUsername());
            feed.setDescription(String.format("%s - Piped", pl.getName()));
            feed.setLink(Constants.FRONTEND_URL + "/playlist?list=" + pl.getPlaylistId());
            feed.setPublishedDate(new Date());

            var videos = playlistVideosCompletableFuture.get();

            for (var video : videos) {
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

    public static byte[] createPlaylist(String session, String name) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(name))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and name are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

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

    public static byte[] renamePlaylistResponse(String session, String playlistId, String newName) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and playlistId are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var playlist = PlaylistHelpers.getUserPlaylist(s, user, playlistId);
            playlist.setName(newName);

            var tr = s.beginTransaction();
            s.merge(playlist);
            tr.commit();

        } catch (IllegalArgumentException e) {
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse(e.getMessage()));
        }

        return mapper.writeValueAsBytes(new AcceptedResponse());
    }

    public static byte[] editPlaylistDescriptionResponse(String session, String playlistId, String newDescription)
            throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId) || StringUtils.isBlank(newDescription))
            ExceptionHandler
                    .throwErrorResponse(
                            new InvalidRequestResponse("session, playlistId and description are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var playlist = PlaylistHelpers.getUserPlaylist(s, user, playlistId);
            playlist.setShortDescription(newDescription);

            var tr = s.beginTransaction();
            s.merge(playlist);
            tr.commit();

        } catch (IllegalArgumentException e) {
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse(e.getMessage()));
        }

        return mapper.writeValueAsBytes(new AcceptedResponse());
    }

    public static byte[] deletePlaylistResponse(String session, String playlistId) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and playlistId are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var playlist = PlaylistHelpers.getUserPlaylist(s, user, playlistId);

            var tr = s.beginTransaction();
            s.remove(playlist);
            tr.commit();

        } catch (IllegalArgumentException e) {
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse(e.getMessage()));
        }

        return mapper.writeValueAsBytes(new AcceptedResponse());
    }

    public static byte[] addToPlaylistResponse(String session, String playlistId, List<String> videoIds) throws IOException, ExtractionException {

        videoIds = videoIds.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId) || videoIds.isEmpty())
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session, playlistId and videoId(s) are required parameters"));

        var user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            var cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Playlist.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Playlist.class);
            query.where(cb.equal(root.get("playlist_id"), UUID.fromString(playlistId)));
            var playlist = s.createQuery(query).uniqueResult();

            if (playlist == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            if (playlist.getOwner().getId() != user.getId())
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "You are not the owner this playlist"));

            var playlistVideos = DatabaseHelper.getPlaylistVideosFromIds(s, new ObjectOpenHashSet<>(videoIds));

            var videos = playlist.getVideos();

            boolean added = false;

            for (String videoId : videoIds) {
                if (StringUtils.isEmpty(videoId)) continue;

                var video = playlistVideos.stream().filter(v -> v.getId().equals(videoId))
                        .findFirst()
                        .orElse(null);

                if (video == null) {
                    try {
                        StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);

                        String channelId = StringUtils.substringAfter(info.getUploaderUrl(), "/channel/");

                        var channel = DatabaseHelper.getChannelFromId(s, channelId);

                        if (channel == null) {
                            channel = DatabaseHelper.saveChannel(channelId);
                        }

                        video = new PlaylistVideo(videoId, info.getName(), info.getThumbnails().getLast().getUrl(), info.getDuration(), channel);

                        var tr = s.beginTransaction();
                        try {
                            s.persist(video);
                            tr.commit();
                        } catch (Exception e) {
                            tr.rollback();
                            ExceptionHandler.handle(e);
                            continue;
                        }
                    } catch (Exception e) {
                        ExceptionHandler.handle(e);
                        continue;
                    }
                }

                if (playlist.getVideos().isEmpty()) playlist.setThumbnail(video.getThumbnail());

                added = true;

                videos.add(video);
            }

            if (!added) {
                // only return an error if no videos were added
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Unable to add any videos, since they were unable to be fetched"));
            }

            var tr = s.beginTransaction();
            s.merge(playlist);
            tr.commit();

            return mapper.writeValueAsBytes(new AcceptedResponse());
        }
    }

    public static byte[] removeFromPlaylistResponse(String session, String playlistId, int index) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and playlistId are required parameters"));

        if (index < 0)
            return mapper.writeValueAsBytes(mapper.createObjectNode()
                    .put("error", "Video Index out of bounds"));

        long internalId;

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Playlist.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Playlist.class);
            query.where(cb.equal(root.get("playlist_id"), UUID.fromString(playlistId)));
            var playlist = s.createQuery(query).uniqueResult();

            if (playlist == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            if (playlist.getOwner().getId() != DatabaseHelper.getUserFromSession(session).getId())
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "You are not the owner this playlist"));
            internalId = playlist.getId();
        }

        try (Session s = DatabaseSessionFactory.createSession()) {

            var tr = s.beginTransaction();

            var updated = s.createNativeMutationQuery("DELETE FROM playlists_videos_ids WHERE playlist_id = :playlistId AND videos_order = :index")
                    .setParameter("playlistId", internalId)
                    .setParameter("index", index)
                    .executeUpdate();

            if (updated > 0) {
                s.createNativeMutationQuery("UPDATE playlists_videos_ids SET videos_order = videos_order - 1 WHERE playlist_id = :playlistId AND videos_order > :index")
                        .setParameter("playlistId", internalId)
                        .setParameter("index", index)
                        .executeUpdate();
            } else
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Video Index not found"));

            tr.commit();
        }

        return mapper.writeValueAsBytes(new AcceptedResponse());
    }

    public static byte[] clearPlaylistResponse(String session, String playlistId) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and playlistId are required parameters"));

        try (Session s = DatabaseSessionFactory.createSession()) {
            var cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Playlist.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Playlist.class);
            query.where(cb.equal(root.get("playlist_id"), UUID.fromString(playlistId)));
            var playlist = s.createQuery(query).uniqueResult();

            if (playlist == null)
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "Playlist not found"));

            if (playlist.getOwner().getId() != DatabaseHelper.getUserFromSession(session).getId())
                return mapper.writeValueAsBytes(mapper.createObjectNode()
                        .put("error", "You are not the owner this playlist"));

            playlist.setVideos(new ObjectArrayList<>());

            var tr = s.beginTransaction();
            s.merge(playlist);
            tr.commit();

            return mapper.writeValueAsBytes(new AcceptedResponse());
        }
    }

    public static byte[] importPlaylistResponse(String session, String playlistId) throws IOException, ExtractionException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and playlistId are required parameters"));

        var user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        final String url = "https://www.youtube.com/playlist?list=" + playlistId;

        PlaylistInfo info = PlaylistInfo.getInfo(url);

        var playlist = new me.kavin.piped.utils.obj.db.Playlist(info.getName(), user, info.getThumbnails().getLast().getUrl());

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

            Map<String, Channel> channelMap = new Object2ObjectOpenHashMap<>();

            var channels = DatabaseHelper.getChannelsFromIds(s, channelIds);
            channelIds.forEach(id -> {
                var fetched = channels.stream().filter(channel -> channel.getUploaderId().equals(id)).findFirst()
                        .orElseGet(() -> DatabaseHelper.saveChannel(id));
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

                playlist.getVideos().add(videoMap.computeIfAbsent(videoId, (key) -> new PlaylistVideo(videoId, video.getName(), video.getThumbnails().getLast().getUrl(), video.getDuration(), channel)));
            });

            var tr = s.beginTransaction();
            s.persist(playlist);
            tr.commit();
        }

        return mapper.writeValueAsBytes(mapper.createObjectNode()
                .put("playlistId", String.valueOf(playlist.getPlaylistId()))
        );
    }

    public static byte[] playlistsResponse(String session) throws IOException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        try (Session s = DatabaseSessionFactory.createSession()) {

            User user = DatabaseHelper.getUserFromSession(session, s);

            if (user == null)
                ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

            var playlists = new ObjectArrayList<>();

            // Select user playlists and count the number of videos in each playlist
            var query = s.createQuery("SELECT p, COUNT(v) FROM Playlist p LEFT JOIN p.videos v WHERE p.owner = :owner GROUP BY p.id", Object[].class);
            query.setParameter("owner", user);
            for (Object[] row : query.list()) {
                var playlist = (me.kavin.piped.utils.obj.db.Playlist) row[0];
                var videoCount = (long) row[1];

                ObjectNode node = mapper.createObjectNode();
                node.put("id", String.valueOf(playlist.getPlaylistId()));
                node.put("name", playlist.getName());
                node.put("shortDescription", playlist.getShortDescription());
                node.put("thumbnail", rewriteURL(playlist.getThumbnail()));
                node.put("videos", videoCount);
                playlists.add(node);
            }

            return mapper.writeValueAsBytes(playlists);
        }
    }
}
