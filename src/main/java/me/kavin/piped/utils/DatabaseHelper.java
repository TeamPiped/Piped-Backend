package me.kavin.piped.utils;

import java.util.LinkedHashSet;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;

import org.hibernate.Session;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;

public class DatabaseHelper {

    public static final User getUserFromSession(Session s, String session) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<User> cr = cb.createQuery(User.class);
        Root<User> root = cr.from(User.class);
        cr.select(root).where(root.get("sessionId").in(session));

        return s.createQuery(cr).uniqueResult();
    }

    public static final User getUserFromSessionWithSubscribed(Session s, String session) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<User> cr = cb.createQuery(User.class);
        Root<User> root = cr.from(User.class);
        root.fetch("subscribed_ids", JoinType.LEFT);
        cr.select(root).where(root.get("sessionId").in(session));

        return s.createQuery(cr).uniqueResult();
    }

    public static final Channel getChannelFromId(Session s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Channel> cr = cb.createQuery(Channel.class);
        Root<Channel> root = cr.from(Channel.class);
        cr.select(root).where(root.get("uploader_id").in(id));

        return s.createQuery(cr).uniqueResult();
    }

    public static final List<Video> getVideosFromChannelIds(Session s, List<String> id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Video> cr = cb.createQuery(Video.class);
        Root<Video> root = cr.from(Video.class);
        root.fetch("channel", JoinType.LEFT);
        cr.select(root).where(root.get("channel").get("uploader_id").in(id));

        return s.createQuery(cr).getResultList();
    }

    public static final List<String> getGlobalSubscribedChannelIds(Session s) {

        @SuppressWarnings("unchecked")
        List<String> subscriptions = new ObjectArrayList<>(
                new LinkedHashSet<>(s.createNativeQuery("Select channel from users_subscribed").getResultList()));

        return subscriptions;
    }

    public static final Video getVideoFromId(Session s, String id) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Video> cr = cb.createQuery(Video.class);
        Root<Video> root = cr.from(Video.class);
        cr.select(root).where(root.get("id").in(id));

        return s.createQuery(cr).uniqueResult();
    }
}
