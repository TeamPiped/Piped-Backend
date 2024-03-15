package me.kavin.piped.utils;

import me.kavin.piped.utils.obj.db.Playlist;
import me.kavin.piped.utils.obj.db.User;
import org.hibernate.Session;

public class PlaylistHelpers {
    public static Playlist getUserPlaylist(Session s, User user, String playlistId) throws IllegalArgumentException {
        var playlist = DatabaseHelper.getPlaylistFromId(s, playlistId);

        if (playlist == null)
            throw new IllegalArgumentException("Playlist not found");

        if (playlist.getOwner().getId() != user.getId())
            throw  new IllegalArgumentException("You do not own this playlist");

        return playlist;
    }
}
