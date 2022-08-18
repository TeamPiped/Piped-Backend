package me.kavin.piped.utils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import me.kavin.piped.utils.obj.db.*;
import org.hibernate.SharedSessionContract;
import org.hibernate.StatelessSession;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class DatabaseHelper {

    public static User getUserFromSession(String session) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            return getUserFromSession(session, s);
        }
    }

    public static User getUserFromSession(String session, SharedSessionContract s) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<User> cr = cb.createQuery(User.class);
        Root<User> root = cr.from(User.class);
        cr.select(root).where(cb.equal(root.get("sessionId"), session));

        return s.createQuery(cr).uniqueResult();
    }

    public static User getUserFromSessionWithSubscribed(String session) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<User> cr = cb.createQuery(User.class);
            Root<User> root = cr.from(User.class);
            root.fetch("subscribed_ids", JoinType.LEFT);
            cr.select(root).where(cb.equal(root.get("sessionId"), session));

            return s.createQuery(cr).uniqueResult();
        }
    }

    public static Channel getChannelFromId(SharedSessionContract s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Channel> cr = cb.createQuery(Channel.class);
        Root<Channel> root = cr.from(Channel.class);
        cr.select(root).where(cb.equal(root.get("uploader_id"), id));

        return s.createQuery(cr).uniqueResult();
    }

    public static Channel getChannelFromId(String id) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            return getChannelFromId(s, id);
        }
    }

    public static List<Channel> getChannelsFromIds(SharedSessionContract s, Collection<String> id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Channel> cr = cb.createQuery(Channel.class);
        Root<Channel> root = cr.from(Channel.class);
        cr.select(root).where(root.get("uploader_id").in(id));

        return s.createQuery(cr).list();
    }

    public static Video getVideoFromId(SharedSessionContract s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Video> cr = cb.createQuery(Video.class);
        Root<Video> root = cr.from(Video.class);
        cr.select(root).where(cb.equal(root.get("id"), id));

        return s.createQuery(cr).uniqueResult();
    }

    public static Video getVideoFromId(String id) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            return getVideoFromId(s, id);
        }
    }

    public static PlaylistVideo getPlaylistVideoFromId(SharedSessionContract s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<PlaylistVideo> cr = cb.createQuery(PlaylistVideo.class);
        Root<PlaylistVideo> root = cr.from(PlaylistVideo.class);
        cr.select(root).where(cb.equal(root.get("id"), id));

        return s.createQuery(cr).uniqueResult();
    }

    public static Playlist getPlaylistFromId(SharedSessionContract s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Playlist> cr = cb.createQuery(Playlist.class);
        Root<Playlist> root = cr.from(Playlist.class);
        cr.select(root).where(cb.equal(root.get("playlist_id"), UUID.fromString(id)));

        return s.createQuery(cr).uniqueResult();
    }

    public static List<PlaylistVideo> getPlaylistVideosFromIds(SharedSessionContract s, Collection<String> id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<PlaylistVideo> cr = cb.createQuery(PlaylistVideo.class);
        Root<PlaylistVideo> root = cr.from(PlaylistVideo.class);
        cr.select(root).where(root.get("id").in(id));

        return s.createQuery(cr).list();
    }

    public static PubSub getPubSubFromId(SharedSessionContract s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<PubSub> cr = cb.createQuery(PubSub.class);
        Root<PubSub> root = cr.from(PubSub.class);
        cr.select(root).where(cb.equal(root.get("id"), id));

        return s.createQuery(cr).uniqueResult();
    }

    public static PubSub getPubSubFromId(String id) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            return getPubSubFromId(s, id);
        }
    }
}
