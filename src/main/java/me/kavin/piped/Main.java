package me.kavin.piped;

import io.activej.inject.Injector;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.db.PubSub;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
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
                    System.out.println(String.format("ThrottlingCache: %o entries", YoutubeThrottlingDecrypter.getCacheSize()));
                    YoutubeThrottlingDecrypter.clearCache();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(60));

        new Thread(() -> {
            try {
                new ServerLauncher().launch(args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

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
                    criteria.select(root)
                            .where(cb.and(
                                    cb.lessThan(root.get("subbedAt"), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4)),
                                    cb.isMember(root.get("id"), userRoot.<Collection<String>>get("subscribed_ids"))
                            )).distinct(true);

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
                try (Session s = DatabaseSessionFactory.createSession()) {

                    var cb = s.getCriteriaBuilder();
                    var cd = cb.createCriteriaDelete(Video.class);
                    var root = cd.from(Video.class);
                    cd.where(cb.lessThan(root.get("uploaded"), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)));

                    Transaction tr = s.beginTransaction();

                    var query = s.createMutationQuery(cd);

                    System.out.println(String.format("Cleanup: Removed %o old videos", query.executeUpdate()));

                    tr.commit();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(60));

    }
}
