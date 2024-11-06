package me.kavin.piped.server.handlers.auth;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import jakarta.persistence.criteria.CriteriaBuilder;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.StreamItem;
import me.kavin.piped.utils.obj.SubscriptionChannel;
import me.kavin.piped.utils.obj.db.Channel;
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
import org.schabi.newpipe.extractor.channel.ChannelInfo;

import javax.annotation.Nullable;
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

        if (!ChannelHelpers.isValidId(channelId))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("channelId is not a valid YouTube channel ID"));

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

                List<StreamItem> feedItems = FeedHelpers.generateAuthenticatedFeed(s, user.getId(), Integer.MAX_VALUE)
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

    public static byte[] feedResponseRSS(String session, @Nullable String filter) throws FeedException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user != null) {
            try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                SyndFeed feed = FeedHelpers.createRssFeed(user.getUsername());

                final List<SyndEntry> entries = FeedHelpers.generateAuthenticatedFeed(s, user.getId(), 100)
                        .filter(FeedHelpers.createFeedFilter(filter))
                        .map(video -> {
                            var channel = video.getChannel();
                            return ChannelHelpers.createEntry(video, channel);
                        }).toList();

                feed.setEntries(entries);

                return new SyndFeedOutput().outputString(feed).getBytes(UTF_8);
            }
        }

        ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());
        return null;
    }

    public static byte[] unauthenticatedFeedResponse(String[] channelIds) throws Exception {

        Set<String> filteredChannels = Arrays.stream(channelIds)
                .filter(ChannelHelpers::isValidId)
                .collect(Collectors.toUnmodifiableSet());

        if (filteredChannels.isEmpty())
            return mapper.writeValueAsBytes(Collections.EMPTY_LIST);

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            List<StreamItem> feedItems = FeedHelpers.generateUnauthenticatedFeed(s, filteredChannels, Integer.MAX_VALUE)
                    .parallel().map(video -> {
                        var channel = video.getChannel();

                        return new StreamItem("/watch?v=" + video.getId(), video.getTitle(),
                                rewriteURL(video.getThumbnail()), channel.getUploader(), "/channel/" + channel.getUploaderId(),
                                rewriteURL(channel.getUploaderAvatar()), null, null, video.getDuration(), video.getViews(),
                                video.getUploaded(), channel.isVerified(), video.isShort());
                    }).toList();

            updateSubscribedTime(filteredChannels);
            addMissingChannels(filteredChannels);

            return mapper.writeValueAsBytes(feedItems);
        }
    }

    public static byte[] unauthenticatedFeedResponseRSS(String[] channelIds, @Nullable String filter) throws Exception {

        Set<String> filteredChannels = Arrays.stream(channelIds)
                .filter(ChannelHelpers::isValidId)
                .collect(Collectors.toUnmodifiableSet());

        if (filteredChannels.isEmpty())
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("No valid channel IDs provided"));

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            List<Video> videos = FeedHelpers.generateUnauthenticatedFeed(s, filteredChannels, 100)
                    .filter(FeedHelpers.createFeedFilter(filter))
                    .toList();

            List<SyndEntry> entries = videos.stream()
                    .map(video -> ChannelHelpers.createEntry(video, video.getChannel()))
                    .toList();

            SyndFeed feed = FeedHelpers.createRssFeed(null);

            if (filteredChannels.size() == 1) {
                if (!videos.isEmpty()) {
                    ChannelHelpers.addChannelInformation(feed, videos.get(0).getChannel());
                } else {
                    String channelId = filteredChannels.stream().findFirst().get();
                    final ChannelInfo info = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);
                    var channel = DatabaseHelper.getChannelFromId(channelId);

                    if (channel == null) channel = new Channel();

                    ChannelHelpers.updateChannel(s, channel, StringUtils.abbreviate(info.getName(), 100), info.getAvatars().isEmpty() ? null : info.getAvatars().getLast().getUrl(), info.isVerified());

                    ChannelHelpers.addChannelInformation(feed, channel);
                }
            }

            feed.setEntries(entries);

            updateSubscribedTime(filteredChannels);
            addMissingChannels(filteredChannels);

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
                var query = cb.createQuery(Channel.class);
                var root = query.from(Channel.class);
                var subquery = query.subquery(String.class);
                var subroot = subquery.from(User.class);

                subquery.select(subroot.get("subscribed_ids"))
                        .where(cb.equal(subroot.get("id"), user.getId()));

                query.select(root)
                        .where(root.get("uploader_id").in(subquery));

                List<SubscriptionChannel> subscriptionItems = FeedHelpers
                        .generateSubscriptionsList(s.createQuery(query).stream())
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
                .filter(ChannelHelpers::isValidId)
                .collect(Collectors.toUnmodifiableSet());

        if (filtered.isEmpty())
            return mapper.writeValueAsBytes(Collections.EMPTY_LIST);

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

            CriteriaBuilder cb = s.getCriteriaBuilder();
            var query = cb.createQuery(Channel.class);
            var root = query.from(Channel.class);
            query.select(root);
            query.where(root.get("uploader_id").in(filtered));

            List<SubscriptionChannel> subscriptionItems = FeedHelpers
                    .generateSubscriptionsList(s.createQuery(query).stream())
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
