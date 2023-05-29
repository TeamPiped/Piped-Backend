package me.kavin.piped.utils;

import me.kavin.piped.utils.obj.db.Playlist;

public final class PlaylistResult {
    private final Playlist playlist;
    private final String error;

    public PlaylistResult(Playlist playlist, String error) {
        this.playlist = playlist;
        this.error = error;
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public String getError() {
        return error;
    }
}
