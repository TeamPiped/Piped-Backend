package me.kavin.piped.server.handlers.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.utils.DatabaseHelper;
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.obj.db.PlaylistBookmark;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.resp.AcceptedResponse;
import me.kavin.piped.utils.resp.AuthenticationFailureResponse;
import me.kavin.piped.utils.resp.BookmarkedStatusResponse;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;

import java.io.IOException;

import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.URLUtils.*;

public class PlaylistBookmarkHandlers {
    public static byte[] createPlaylistBookmarkResponse(String session, String playlistId) throws IOException, ExtractionException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and name are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null) ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            if (DatabaseHelper.isBookmarked(s, user, playlistId)) {
                var bookmark = DatabaseHelper.getPlaylistBookmarkFromPlaylistId(s, user, playlistId);
                return mapper.writeValueAsBytes(createPlaylistBookmarkResponseItem(bookmark));
            }

            final PlaylistInfo info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=" + playlistId);

            var playlistBookmark = new PlaylistBookmark(playlistId, info.getName(), info.getDescription().getContent(), getLastThumbnail(info.getThumbnails()), info.getUploaderName(), substringYouTube(info.getUploaderUrl()), getLastThumbnail(info.getUploaderAvatars()), info.getStreamCount(), user);

            var tr = s.beginTransaction();
            s.persist(playlistBookmark);
            tr.commit();

            ObjectNode response = createPlaylistBookmarkResponseItem(playlistBookmark);

            return mapper.writeValueAsBytes(response);
        }
    }

    public static byte[] deletePlaylistBookmarkResponse(String session, String playlistId) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and playlistId are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null) ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {

            DatabaseHelper.deletePlaylistBookmark(s, user, playlistId);

            return mapper.writeValueAsBytes(new AcceptedResponse());
        }
    }

    public static byte[] playlistBookmarksResponse(String session) throws IOException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null) ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {

            var responseArray = new ObjectArrayList<>();
            var playlistBookmarks = DatabaseHelper.getPlaylistBookmarks(s, user);

            for (PlaylistBookmark bookmark : playlistBookmarks) {
                responseArray.add(createPlaylistBookmarkResponseItem(bookmark));
            }

            return mapper.writeValueAsBytes(responseArray);
        }
    }

    public static byte[] isBookmarkedResponse(String session, String playlistId) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(playlistId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and playlistId are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null) ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

        try (Session s = DatabaseSessionFactory.createSession()) {
            boolean isBookmarked = DatabaseHelper.isBookmarked(s, user, playlistId);

            return mapper.writeValueAsBytes(new BookmarkedStatusResponse(isBookmarked));
        }
    }

    private static ObjectNode createPlaylistBookmarkResponseItem(PlaylistBookmark bookmark) {
        ObjectNode node = mapper.createObjectNode();
        node.put("playlistId", String.valueOf(bookmark.getPlaylistId()));
        node.put("name", bookmark.getName());
        node.put("shortDescription", bookmark.getShortDescription());
        node.put("thumbnailUrl", rewriteURL(bookmark.getThumbnailUrl()));
        node.put("uploader", bookmark.getUploader());
        node.put("uploaderUrl", bookmark.getUploaderUrl());
        node.put("uploaderAvatar", rewriteURL(bookmark.getUploaderAvatar()));
        node.put("videos", bookmark.getVideoCount());
        return node;
    }
}
