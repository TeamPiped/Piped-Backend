package me.kavin.piped;

import io.activej.inject.Injector;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.db.*;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeThrottlingDecrypter;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {

        NewPipe.init(new DownloaderImpl(), new Localization("en", "US"));
        YoutubeStreamExtractor.forceFetchIosClient(true);

        Injector.useSpecializer();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.printf("ThrottlingCache: %o entries%n", YoutubeThrottlingDecrypter.getCacheSize());
                    YoutubeThrottlingDecrypter.clearCache();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(60));

        if (!Constants.DISABLE_SERVER)
            new Thread(() -> {
                try {
                    new ServerLauncher().launch(args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();

        try (Session ignored = DatabaseSessionFactory.createSession()) {
            System.out.println("Database connection is ready!");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        if (Constants.DISABLE_TIMERS)
            return;

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                    CriteriaBuilder cb = s.getCriteriaBuilder();
                    CriteriaQuery<PubSub> criteria = cb.createQuery(PubSub.class);
                    var root = criteria.from(PubSub.class);
                    var userRoot = criteria.from(User.class);
                    var subquery = criteria.subquery(UnauthenticatedSubscription.class);
                    var subRoot = subquery.from(UnauthenticatedSubscription.class);
                    subquery.select(subRoot.get("id"))
                            .where(cb.gt(subRoot.get("subscribedAt"), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.SUBSCRIPTIONS_EXPIRY)));
                    criteria.select(root)
                            .where(cb.or(
                                    cb.and(
                                            cb.lessThan(root.get("subbedAt"), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4)),
                                            cb.isMember(root.get("id"), userRoot.<Collection<String>>get("subscribed_ids"))
                                    ),
                                    root.get("id").in(subquery)
                            ));

                    List<PubSub> pubSubList = s.createQuery(criteria).list();

                    Collections.shuffle(pubSubList);

                    pubSubList.stream().parallel()
                            .forEach(pubSub -> {
                                if (pubSub != null)
                                    Multithreading.runAsyncLimitedPubSub(() -> {
                                        try {
                                            ResponseHelper.subscribePubSub(pubSub.getId());
                                        } catch (IOException e) {
                                            ExceptionHandler.handle(e);
                                        }
                                    });
                            });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(90));

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                    var cb = s.getCriteriaBuilder();
                    var cd = cb.createCriteriaDelete(Video.class);
                    var root = cd.from(Video.class);
                    cd.where(cb.lessThan(root.get("uploaded"), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)));

                    var tr = s.beginTransaction();

                    var query = s.createMutationQuery(cd);

                    System.out.printf("Cleanup: Removed %o old videos%n", query.executeUpdate());

                    tr.commit();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(60));

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                    CriteriaBuilder cb = s.getCriteriaBuilder();

                    var pvQuery = cb.createCriteriaDelete(PlaylistVideo.class);
                    var pvRoot = pvQuery.from(PlaylistVideo.class);

                    var subQuery = pvQuery.subquery(me.kavin.piped.utils.obj.db.Playlist.class);
                    var subRoot = subQuery.from(me.kavin.piped.utils.obj.db.Playlist.class);

                    subQuery.select(subRoot.join("videos").get("id"));

                    pvQuery.where(cb.not(pvRoot.get("id").in(subQuery)));

                    var tr = s.beginTransaction();
                    s.createMutationQuery(pvQuery).executeUpdate();
                    tr.commit();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(60));

    }
}
