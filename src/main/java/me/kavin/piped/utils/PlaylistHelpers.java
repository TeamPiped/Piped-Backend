package me.kavin.piped.utils;

import org.hibernate.Session;

import me.kavin.piped.utils.obj.db.User;

public class PlaylistHelpers {
    public static PlaylistResult getUserPlaylist(Session s, User user, String playlistId) {
        var playlist = DatabaseHelper.getPlaylistFromId(s, playlistId);

        if (playlist == null)
            return new PlaylistResult(null, "Playlist not found");

        if (playlist.getOwner().getId() != user.getId())
            return new PlaylistResult(null, "You do not own this playlist");

        return new PlaylistResult(playlist, null);
    }
}
