package me.kavin.piped.utils;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.SubscriptionChannel;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;
import org.hibernate.StatelessSession;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static me.kavin.piped.utils.URLUtils.rewriteURL;

public class FeedHelpers {
    public static Stream<Video> generateAuthenticatedFeed(StatelessSession s, long userId, int maxResults) {
        CriteriaBuilder cb = s.getCriteriaBuilder();

        // Get all videos from subscribed channels, with channel info
        CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
        var root = criteria.from(Video.class);
        root.fetch("channel", JoinType.RIGHT);
        var subquery = criteria.subquery(String.class);
        var subroot = subquery.from(User.class);
        subquery.select(subroot.get("subscribed_ids"))
                .where(cb.equal(subroot.get("id"), userId));

        criteria.select(root)
                .where(
                        root.get("channel").get("uploader_id").in(subquery)
                )
                .orderBy(cb.desc(root.get("uploaded")));

        return s.createQuery(criteria).setTimeout(20).setMaxResults(maxResults).stream();
    }

    public static Stream<Video> generateUnauthenticatedFeed(StatelessSession s, Set<String> channelIds, int maxResults) {
        CriteriaBuilder cb = s.getCriteriaBuilder();

        // Get all videos from subscribed channels, with channel info
        CriteriaQuery<Video> criteria = cb.createQuery(Video.class);
        var root = criteria.from(Video.class);
        root.fetch("channel", JoinType.RIGHT);

        criteria.select(root)
                .where(cb.and(
                        root.get("channel").get("id").in(channelIds)
                ))
                .orderBy(cb.desc(root.get("uploaded")));

        return s.createQuery(criteria)
                .setTimeout(20)
                .setMaxResults(maxResults)
                .stream();
    }

    public static SyndFeed createRssFeed(@Nullable String username) {
        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("atom_1.0");
        feed.setTitle("Piped - Feed");

        if (username == null) {
            feed.setDescription("Piped's RSS unauthenticated subscription feed.");
        } else {
            feed.setDescription(String.format("Piped's RSS subscription feed for %s.", username));
        }

        feed.setUri(Constants.FRONTEND_URL + "/feed");
        feed.setPublishedDate(new Date());

        return feed;
    }

    public static Predicate<Video> createFeedFilter(@Nullable String filter) {
        return video -> switch (filter) {
            case "shorts" -> video.isShort();
            case "videos" -> !video.isShort();
            case null, default -> true;
        };
    }

    public static Stream<SubscriptionChannel> generateSubscriptionsList(Stream<Channel> channels) {
        return channels.parallel()
                .filter(channel -> channel.getUploader() != null)
                .sorted(Comparator.comparing(Channel::getUploader, String.CASE_INSENSITIVE_ORDER))
                .map(channel -> new SubscriptionChannel("/channel/" + channel.getUploaderId(),
                        channel.getUploader(), rewriteURL(channel.getUploaderAvatar()), channel.isVerified()));
    }
}
