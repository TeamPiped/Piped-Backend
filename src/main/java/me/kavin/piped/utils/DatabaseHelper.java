package me.kavin.piped.utils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Session;
import org.hibernate.SharedSessionContract;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;

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

    public static List<Video> getVideosFromIds(SharedSessionContract s, Collection<String> ids) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Video> cr = cb.createQuery(Video.class);
        Root<Video> root = cr.from(Video.class);
        cr.select(root).where(root.get("id").in(ids));

        return s.createQuery(cr).list();
    }

    public static Video getVideoFromId(String id) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            return getVideoFromId(s, id);
        }
    }

    public static boolean doesVideoExist(SharedSessionContract s, String id) {
        return s.createQuery("SELECT 1 FROM Video WHERE id = :id", Integer.class)
                .setParameter("id", id)
                .setMaxResults(1)
                .uniqueResultOptional()
                .isPresent();
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

    public static Playlist getPlaylistFromId(String id) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            return getPlaylistFromId(s, id);
        }
    }

    public static List<PlaylistVideo> getPlaylistVideosFromPlaylistId(SharedSessionContract s, String id, boolean fetchChannel) {
        var query = s.createNativeQuery("SELECT {playlist_videos.*}, {channels.*} FROM playlist_videos JOIN channels ON playlist_videos.uploader_id = channels.uploader_id JOIN playlists_videos_ids ON playlist_videos.id = playlists_videos_ids.videos_id JOIN playlists ON playlists.id = playlists_videos_ids.playlist_id WHERE playlists.playlist_id = :id ORDER BY playlists_videos_ids.videos_order ASC")
                .addEntity("playlist_videos", PlaylistVideo.class)
                .addEntity("channels", Channel.class)
                .setParameter("id", UUID.fromString(id));

        return query.getResultList().stream().map(o -> {
            var arr = (Object[]) o;
            var pv = ((PlaylistVideo) arr[0]);
            pv.setChannel((Channel) arr[1]);
            return pv;
        }).toList();
    }

    public static List<PlaylistVideo> getPlaylistVideosFromPlaylistId(String id, boolean fetchChannel) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            return getPlaylistVideosFromPlaylistId(s, id, fetchChannel);
        }
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

    public static Channel saveChannel(String channelId) {

        if (!ChannelHelpers.isValidId(channelId))
            return null;


        final ChannelInfo info;

        try {
            info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);
        } catch (IOException | ExtractionException e) {
            ExceptionUtils.rethrow(e);
            return null;
        }

        var channel = new Channel(channelId, StringUtils.abbreviate(info.getName(), 100),
                info.getAvatars().isEmpty() ? null : info.getAvatars().getLast().getUrl(), info.isVerified());

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var tr = s.beginTransaction();
            s.insert(channel);
            tr.commit();
        } catch (Exception e) {
            ExceptionHandler.handle(e);
        }

        Multithreading.runAsync(() -> {
            try {
                PubSubHelper.subscribePubSub(channelId);
            } catch (IOException e) {
                ExceptionHandler.handle(e);
            }
        });

        Multithreading.runAsync(() -> {
            CollectionUtils.collectPreloadedTabs(info.getTabs())
                    .stream()
                    .parallel()
                    .mapMulti((tab, consumer) -> {
                        try {
                            ChannelTabInfo.getInfo(YOUTUBE_SERVICE, tab)
                                    .getRelatedItems()
                                    .forEach(consumer);
                        } catch (ExtractionException | IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .filter(StreamInfoItem.class::isInstance)
                    .map(StreamInfoItem.class::cast)
                    .forEach(item -> {
                        long time = item.getUploadDate() != null
                                ? item.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                                : System.currentTimeMillis();
                        if ((System.currentTimeMillis() - time) < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION))
                            VideoHelpers.handleNewVideo(item.getUrl(), time, channel);
                    });
        });

        return channel;
    }

    public static void setOidcData(OidcData data) {
        try (Session s = DatabaseSessionFactory.createSession()) {
            var tr = s.beginTransaction();
            s.persist(data);
            tr.commit();
        }
    }

    public static OidcData getOidcData(String state) {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<OidcData> cr = cb.createQuery(OidcData.class);
            Root<OidcData> root = cr.from(OidcData.class);
            cr.select(root).where(cb.equal(root.get("state"), state));

            return s.createQuery(cr).uniqueResult();
        }
    }
}
