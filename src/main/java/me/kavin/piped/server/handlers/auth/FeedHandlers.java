package me.kavin.piped.server.handlers.auth;

import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DatabaseHelper;
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.Multithreading;
import me.kavin.piped.utils.obj.StreamItem;
import me.kavin.piped.utils.obj.SubscriptionChannel;
import me.kavin.piped.utils.obj.db.UnauthenticatedSubscription;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;
import me.kavin.piped.utils.resp.AcceptedResponse;
import me.kavin.piped.utils.resp.AuthenticationFailureResponse;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import me.kavin.piped.utils.resp.SubscribeStatusResponse;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.StatelessSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.URLUtils.rewriteURL;

public class FeedHandlers {
    public static byte[] subscribeResponse(String session, String channelId)
            throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(channelId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and channelId are required parameters"));

        try (Session s = DatabaseSessionFactory.createSession()) {

            User user = DatabaseHelper.getUserFromSessionWithSubscribed(session);

            if (user != null) {
                if (!user.getSubscribed().contains(channelId)) {

                    user.getSubscribed().add(channelId);

                    var tr = s.beginTransaction();
                    s.merge(user);
                    tr.commit();

                    Multithreading.runAsync(() -> {
                        var channel = DatabaseHelper.getChannelFromId(channelId);
                        if (channel == null) {
                            Multithreading.runAsync(() -> DatabaseHelper.saveChannel(channelId));
                        }
                    });
                }

                return mapper.writeValueAsBytes(new AcceptedResponse());
            }


            ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());
            return null;
        }

    }

    public static byte[] isSubscribedResponse(String session, String channelId) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(channelId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and channelId are required parameters"));

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var cb = s.getCriteriaBuilder();
            var query = cb.createQuery(Long.class);
            var root = query.from(User.class);
            query.select(cb.count(root))
                    .where(cb.and(
                            cb.equal(root.get("sessionId"), session),
                            cb.isMember(channelId, root.get("subscribed_ids"))
                    ));
            var subscribed = s.createQuery(query).getSingleResult() > 0;

            return mapper.writeValueAsBytes(new SubscribeStatusResponse(subscribed));
        }
    }

    public static byte[] feedResponse(String session) throws IOException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                CriteriaBuilder cb = s.getCriteriaBuilder();

                // Get all videos from subscribed channels, with channel info
                CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
                var root = criteria.from(Video.class);
                root.fetch("channel", JoinType.INNER);
                var subquery = criteria.subquery(User.class);
                var subroot = subquery.from(User.class);
                subquery.select(subroot.get("subscribed_ids"))
                        .where(cb.equal(subroot.get("id"), user.getId()));

                criteria.select(root)
                        .where(
                                root.get("channel").in(subquery)
                        )
                        .orderBy(cb.desc(root.get("uploaded")));

                List<StreamItem> feedItems = s.createQuery(criteria).setTimeout(20).stream()
                        .parallel().map(video -> {
                            var channel = video.getChannel();

                            return new StreamItem("/watch?v=" + video.getId(), video.getTitle(),
                                    rewriteURL(video.getThumbnail()), channel.getUploader(), "/channel/" + channel.getUploaderId(),
                                    rewriteURL(channel.getUploaderAvatar()), null, null, video.getDuration(), video.getViews(),
                                    video.getUploaded(), channel.isVerified(), video.isShort());
                        }).toList();

                return mapper.writeValueAsBytes(feedItems);
            }
        }

        ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());
        return null;
    }

    public static byte[] feedResponseRSS(String session) throws FeedException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {

            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                SyndFeed feed = new SyndFeedImpl();
                feed.setFeedType("atom_1.0");
                feed.setTitle("Piped - Feed");
                feed.setDescription(String.format("Piped's RSS subscription feed for %s.", user.getUsername()));
                feed.setUri(Constants.FRONTEND_URL + "/feed");
                feed.setPublishedDate(new Date());

                CriteriaBuilder cb = s.getCriteriaBuilder();

                // Get all videos from subscribed channels, with channel info
                CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
                var root = criteria.from(Video.class);
                root.fetch("channel", JoinType.INNER);
                var subquery = criteria.subquery(User.class);
                var subroot = subquery.from(User.class);
                subquery.select(subroot.get("subscribed_ids"))
                        .where(cb.equal(subroot.get("id"), user.getId()));

                criteria.select(root)
                        .where(
                                root.get("channel").in(subquery)
                        )
                        .orderBy(cb.desc(root.get("uploaded")));

                final List<SyndEntry> entries = s.createQuery(criteria)
                        .setTimeout(20)
                        .setMaxResults(100)
                        .stream()
                        .map(video -> {
                            var channel = video.getChannel();
                            SyndEntry entry = new SyndEntryImpl();

                            SyndPerson person = new SyndPersonImpl();
                            person.setName(channel.getUploader());
                            person.setUri(Constants.FRONTEND_URL + "/channel/" + channel.getUploaderId());

                            entry.setAuthors(Collections.singletonList(person));

                            entry.setLink(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                            entry.setUri(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                            entry.setTitle(video.getTitle());
                            entry.setPublishedDate(new Date(video.getUploaded()));

                            return entry;
                        }).toList();

                feed.setEntries(entries);

                return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);
            }
        }

        ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());
        return null;
    }

    public static byte[] unauthenticatedFeedResponse(String[] channelIds) throws Exception {

        Set<String> filtered = Arrays.stream(channelIds)
                .filter(StringUtils::isNotBlank)
                .filter(id -> id.matches("[A-Za-z\\d_-]+"))
                .collect(Collectors.toUnmodifiableSet());

        if (filtered.isEmpty())
            return mapper.writeValueAsBytes(Collections.EMPTY_LIST);

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            CriteriaBuilder cb = s.getCriteriaBuilder();

            // Get all videos from subscribed channels, with channel info
            CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
            var root = criteria.from(Video.class);
            root.fetch("channel", JoinType.INNER);

            criteria.select(root)
                    .where(cb.and(
                            root.get("channel").get("id").in(filtered)
                    ))
                    .orderBy(cb.desc(root.get("uploaded")));

            List<StreamItem> feedItems = s.createQuery(criteria).setTimeout(20).stream()
                    .parallel().map(video -> {
                        var channel = video.getChannel();

                        return new StreamItem("/watch?v=" + video.getId(), video.getTitle(),
                                rewriteURL(video.getThumbnail()), channel.getUploader(), "/channel/" + channel.getUploaderId(),
                                rewriteURL(channel.getUploaderAvatar()), null, null, video.getDuration(), video.getViews(),
                                video.getUploaded(), channel.isVerified(), video.isShort());
                    }).toList();

            updateSubscribedTime(filtered);
            addMissingChannels(filtered);

            return mapper.writeValueAsBytes(feedItems);
        }
    }

    public static byte[] unauthenticatedFeedResponseRSS(String[] channelIds) throws Exception {

        Set<String> filtered = Arrays.stream(channelIds)
                .filter(StringUtils::isNotBlank)
                .filter(id -> id.matches("[A-Za-z\\d_-]+"))
                .collect(Collectors.toUnmodifiableSet());

        if (filtered.isEmpty())
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("No valid channel IDs provided"));

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            CriteriaBuilder cb = s.getCriteriaBuilder();

            // Get all videos from subscribed channels, with channel info
            CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
            var root = criteria.from(Video.class);
            root.fetch("channel", JoinType.INNER);

            criteria.select(root)
                    .where(cb.and(
                            root.get("channel").get("id").in(filtered)
                    ))
                    .orderBy(cb.desc(root.get("uploaded")));

            List<Video> videos = s.createQuery(criteria)
                    .setTimeout(20)
                    .setMaxResults(100)
                    .list();

            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("atom_1.0");
            feed.setTitle("Piped - Feed");
            feed.setDescription("Piped's RSS unauthenticated subscription feed.");
            feed.setUri(Constants.FRONTEND_URL + "/feed");
            feed.setPublishedDate(new Date());

            final List<SyndEntry> entries = new ObjectArrayList<>();

            for (Video video : videos) {
                var channel = video.getChannel();
                SyndEntry entry = new SyndEntryImpl();

                SyndPerson person = new SyndPersonImpl();
                person.setName(channel.getUploader());
                person.setUri(Constants.FRONTEND_URL + "/channel/" + channel.getUploaderId());

                entry.setAuthors(Collections.singletonList(person));

                entry.setLink(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                entry.setUri(Constants.FRONTEND_URL + "/watch?v=" + video.getId());
                entry.setTitle(video.getTitle());
                entry.setPublishedDate(new Date(video.getUploaded()));
                entries.add(entry);
            }

            feed.setEntries(entries);

            updateSubscribedTime(filtered);
            addMissingChannels(filtered);

            return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);
        }
    }

    private static void updateSubscribedTime(Collection<String> channelIds) {
        Multithreading.runAsync(() -> {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                var tr = s.beginTransaction();
                var cb = s.getCriteriaBuilder();
                var cu = cb.createCriteriaUpdate(UnauthenticatedSubscription.class);
                var root = cu.getRoot();
                cu
                        .set(root.get("subscribedAt"), System.currentTimeMillis())
                        .where(cb.and(
                                root.get("id").in(channelIds),
                                cb.lt(root.get("subscribedAt"), System.currentTimeMillis() - (TimeUnit.DAYS.toMillis(Constants.SUBSCRIPTIONS_EXPIRY) / 2))
                        ));
                s.createMutationQuery(cu).executeUpdate();
                tr.commit();
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    private static void addMissingChannels(Collection<String> channelIds) {
        Multithreading.runAsyncLimited(() -> {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                var cb = s.getCriteriaBuilder();

                {
                    var query = cb.createQuery();
                    var root = query.from(UnauthenticatedSubscription.class);
                    query.select(root.get("id"))
                            .where(root.get("id").in(channelIds));

                    List<Object> existing = s.createQuery(query).setTimeout(20).list();

                    var tr = s.beginTransaction();
                    channelIds.stream()
                            .filter(id -> !existing.contains(id))
                            .map(UnauthenticatedSubscription::new)
                            .forEach(s::insert);
                    tr.commit();
                }

                {
                    var query = cb.createQuery();
                    var root = query.from(me.kavin.piped.utils.obj.db.Channel.class);
                    query.select(root.get("id"))
                            .where(root.get("id").in(channelIds));

                    List<Object> existing = s.createQuery(query).setTimeout(20).list();

                    channelIds.stream()
                            .filter(id -> !existing.contains(id))
                            .forEach(id -> Multithreading.runAsyncLimited(() -> DatabaseHelper.saveChannel(id)));
                }

            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
        });
    }

    public static byte[] importResponse(String session, String[] channelIds, boolean override) throws IOException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        User user = DatabaseHelper.getUserFromSessionWithSubscribed(session);

        if (user != null) {

            Multithreading.runAsync(() -> {
                try (Session s = DatabaseSessionFactory.createSession()) {
                    if (override) {
                        user.setSubscribed(Set.of(channelIds));
                    } else {
                        for (String channelId : channelIds)
                            user.getSubscribed().add(channelId);
                    }

                    if (channelIds.length > 0) {
                        var tr = s.beginTransaction();
                        s.merge(user);
                        tr.commit();
                    }
                } catch (Exception e) {
                    ExceptionHandler.handle(e);
                }
            });

            Multithreading.runAsync(() -> {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                    var channels = DatabaseHelper.getChannelsFromIds(s, Arrays.asList(channelIds));

                    Arrays.stream(channelIds).parallel()
                            .filter(channelId ->
                                    channels.stream().parallel()
                                            .filter(channel -> channel.getUploaderId().equals(channelId))
                                            .findFirst().isEmpty()
                            )
                            .forEach(channelId -> Multithreading.runAsyncLimited(() -> DatabaseHelper.saveChannel(channelId)));
                } catch (Exception e) {
                    ExceptionHandler.handle(e);
                }
            });

            return mapper.writeValueAsBytes(new AcceptedResponse());
        }

        ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());
        return null;
    }

    public static byte[] subscriptionsResponse(String session)
            throws IOException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                CriteriaBuilder cb = s.getCriteriaBuilder();
                var query = cb.createQuery(me.kavin.piped.utils.obj.db.Channel.class);
                var root = query.from(me.kavin.piped.utils.obj.db.Channel.class);
                var subquery = query.subquery(User.class);
                var subroot = subquery.from(User.class);

                subquery.select(subroot.get("subscribed_ids"))
                        .where(cb.equal(subroot.get("id"), user.getId()));

                query.select(root)
                        .where(root.get("uploader_id").in(subquery));

                List<SubscriptionChannel> subscriptionItems = s.createQuery(query)
                        .stream().parallel()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(me.kavin.piped.utils.obj.db.Channel::getUploader, String.CASE_INSENSITIVE_ORDER))
                        .map(channel -> new SubscriptionChannel("/channel/" + channel.getUploaderId(),
                                channel.getUploader(), rewriteURL(channel.getUploaderAvatar()), channel.isVerified()))
                        .toList();

                return mapper.writeValueAsBytes(subscriptionItems);
            }
        }

        ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());
        return null;

    }

    public static byte[] unauthenticatedSubscriptionsResponse(String[] channelIds)
            throws IOException {

        Set<String> filtered = Arrays.stream(channelIds)
                .filter(StringUtils::isNotBlank)
                .filter(id -> id.matches("[A-Za-z\\d_-]+"))
                .collect(Collectors.toUnmodifiableSet());

        if (filtered.isEmpty())
            return mapper.writeValueAsBytes(Collections.EMPTY_LIST);

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            CriteriaBuilder cb = s.getCriteriaBuilder();
            var query = cb.createQuery(me.kavin.piped.utils.obj.db.Channel.class);
            var root = query.from(me.kavin.piped.utils.obj.db.Channel.class);
            query.select(root);
            query.where(root.get("uploader_id").in(filtered));

            List<SubscriptionChannel> subscriptionItems = s.createQuery(query)
                    .stream().parallel()
                    .sorted(Comparator.comparing(me.kavin.piped.utils.obj.db.Channel::getUploader, String.CASE_INSENSITIVE_ORDER))
                    .map(channel -> new SubscriptionChannel("/channel/" + channel.getUploaderId(),
                            channel.getUploader(), rewriteURL(channel.getUploaderAvatar()), channel.isVerified()))
                    .toList();

            return mapper.writeValueAsBytes(subscriptionItems);
        }
    }

    public static byte[] unsubscribeResponse(String session, String channelId)
            throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(channelId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and channelId are required parameters"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {
            try (Session s = DatabaseSessionFactory.createSession()) {
                var tr = s.beginTransaction();
                s.createNativeMutationQuery("delete from users_subscribed where subscriber = :id and channel = :channel")
                        .setParameter("id", user.getId()).setParameter("channel", channelId).executeUpdate();
                tr.commit();
                return mapper.writeValueAsBytes(new AcceptedResponse());
            }

        }

        ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());
        return null;

    }
}
