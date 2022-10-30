package me.kavin.piped;

import io.activej.inject.Injector;
import io.sentry.Sentry;
import jakarta.persistence.criteria.CriteriaBuilder;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.server.ServerLauncher;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.db.PlaylistVideo;
import me.kavin.piped.utils.obj.db.Video;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeThrottlingDecrypter;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {

        NewPipe.init(new DownloaderImpl(), new Localization("en", "US"), ContentCountry.DEFAULT, Multithreading.getCachedExecutor());
        YoutubeStreamExtractor.forceFetchAndroidClient(true);
        YoutubeStreamExtractor.forceFetchIosClient(true);

        Sentry.init(options -> {
            options.setDsn(Constants.SENTRY_DSN);
            options.setRelease(Constants.VERSION);
            options.addIgnoredExceptionForType(ErrorResponse.class);
            options.setTracesSampleRate(0.1);
        });

        Injector.useSpecializer();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.printf("ThrottlingCache: %o entries%n", YoutubeThrottlingDecrypter.getCacheSize());
                YoutubeThrottlingDecrypter.clearCache();
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

                    final Random random = new Random();

                    s.createNativeQuery("SELECT id FROM pubsub WHERE subbed_at < :subbedTime AND id IN (" +
                                    "SELECT DISTINCT channel FROM users_subscribed" +
                                    " UNION " +
                                    "SELECT id FROM unauthenticated_subscriptions WHERE subscribed_at > :unauthSubbed" +
                                    ")", String.class)
                            .setParameter("subbedTime", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))
                            .setParameter("unauthSubbed", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.SUBSCRIPTIONS_EXPIRY))
                            .stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet())
                            .stream()
                            .sorted(Comparator.comparingInt(o -> random.nextInt()))
                            .parallel()
                            .forEach(id -> Multithreading.runAsyncLimitedPubSub(() -> {
                                try {
                                    PubSubHelper.subscribePubSub(id);
                                } catch (IOException e) {
                                    ExceptionHandler.handle(e);
                                }
                            }));

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
