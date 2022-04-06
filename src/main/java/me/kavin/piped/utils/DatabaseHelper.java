package me.kavin.piped.utils;

import me.kavin.piped.utils.obj.db.*;
import org.hibernate.Session;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;

public class DatabaseHelper {

    public static User getUserFromSession(String session) {
        try (Session s = DatabaseSessionFactory.createSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<User> cr = cb.createQuery(User.class);
            Root<User> root = cr.from(User.class);
            cr.select(root).where(cb.equal(root.get("sessionId"), session));

            return s.createQuery(cr).uniqueResult();
        }
    }

    public static User getUserFromSessionWithSubscribed(String session) {
        try (Session s = DatabaseSessionFactory.createSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<User> cr = cb.createQuery(User.class);
            Root<User> root = cr.from(User.class);
            root.fetch("subscribed_ids", JoinType.LEFT);
            cr.select(root).where(cb.equal(root.get("sessionId"), session));

            return s.createQuery(cr).uniqueResult();
        }
    }

    public static Channel getChannelFromId(Session s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Channel> cr = cb.createQuery(Channel.class);
        Root<Channel> root = cr.from(Channel.class);
        cr.select(root).where(cb.equal(root.get("uploader_id"), id));

        return s.createQuery(cr).uniqueResult();
    }

    public static Video getVideoFromId(Session s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Video> cr = cb.createQuery(Video.class);
        Root<Video> root = cr.from(Video.class);
        cr.select(root).where(cb.equal(root.get("id"), id));

        return s.createQuery(cr).uniqueResult();
    }

    public static PlaylistVideo getPlaylistVideoFromId(Session s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<PlaylistVideo> cr = cb.createQuery(PlaylistVideo.class);
        Root<PlaylistVideo> root = cr.from(PlaylistVideo.class);
        cr.select(root).where(cb.equal(root.get("id"), id));

        return s.createQuery(cr).uniqueResult();
    }

    public static PubSub getPubSubFromId(Session s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<PubSub> cr = cb.createQuery(PubSub.class);
        Root<PubSub> root = cr.from(PubSub.class);
        cr.select(root).where(cb.equal(root.get("id"), id));

        return s.createQuery(cr).uniqueResult();
    }
}
