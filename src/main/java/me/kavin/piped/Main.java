package me.kavin.piped;

import io.activej.inject.Injector;
import io.sentry.Sentry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import jakarta.persistence.criteria.CriteriaBuilder;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.server.ServerLauncher;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.matrix.SyncRunner;
import me.kavin.piped.utils.obj.MatrixHelper;
import me.kavin.piped.utils.obj.db.PlaylistVideo;
import me.kavin.piped.utils.obj.db.PubSub;
import me.kavin.piped.utils.obj.db.Video;
import okhttp3.OkHttpClient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import rocks.kavin.reqwest4j.ReqwestUtils;

import java.security.Security;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static me.kavin.piped.consts.Constants.*;

public class Main {

    public static void main(String[] args) throws Exception {

        Security.setProperty("crypto.policy", "unlimited");
        Security.addProvider(new BouncyCastleProvider());

        ReqwestUtils.init(REQWEST_PROXY, REQWEST_PROXY_USER, REQWEST_PROXY_PASS);

        NewPipe.init(new DownloaderImpl(), new Localization("en", "US"), ContentCountry.DEFAULT);
        YoutubeParsingHelper.setConsentAccepted(CONSENT_COOKIE);

        // Warm up the extractor
        try {
            StreamInfo.getInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        } catch (Exception ignored) {
        }

        // Find country code, used for georestricted videos
        Thread.ofVirtual().start(() -> {
            try {
                var html = RequestUtils.sendGet("https://www.youtube.com/").get();
                var regex = Pattern.compile("GL\":\"([A-Z]{2})\"", Pattern.MULTILINE);
                var matcher = regex.matcher(html);
                if (matcher.find()) {
                    YOUTUBE_COUNTRY = matcher.group(1);
                }
            } catch (Exception ignored) {
                System.err.println("Failed to get country from YouTube!");
            }
        });

        Sentry.init(options -> {
            options.setDsn(Constants.SENTRY_DSN);
            options.setRelease(Constants.VERSION);
            options.addIgnoredExceptionForType(ErrorResponse.class);
            options.setTracesSampleRate(0.1);
        });

        Injector.useSpecializer();

        try {
            LiquibaseHelper.init();
        } catch (Exception e) {
            ExceptionHandler.handle(e);
            System.exit(1);
        }

        Multithreading.runAsync(() ->  Thread.ofVirtual().start(new SyncRunner(
                new OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build(),
                MATRIX_SERVER,
                MatrixHelper.MATRIX_TOKEN)
        ));

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.printf("ThrottlingCache: %o entries%n", YoutubeJavaScriptPlayerManager.getThrottlingParametersCacheSize());
                YoutubeJavaScriptPlayerManager.clearThrottlingParametersCache();
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
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }

        // Close the HikariCP connection pool
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseSessionFactory::close));

        if (Constants.DISABLE_TIMERS)
            return;

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                    List<String> channelIds = s.createNativeQuery("SELECT id FROM pubsub WHERE subbed_at < :subbedTime AND id IN (" +
                                    "SELECT DISTINCT channel FROM users_subscribed" +
                                    " UNION " +
                                    "SELECT id FROM unauthenticated_subscriptions WHERE subscribed_at > :unauthSubbed" +
                                    ")", String.class)
                            .setParameter("subbedTime", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))
                            .setParameter("unauthSubbed", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.SUBSCRIPTIONS_EXPIRY))
                            .stream()
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toCollection(ObjectArrayList::new));

                    Collections.shuffle(channelIds);

                    var queue = new ConcurrentLinkedQueue<>(channelIds);

                    System.out.println("PubSub: queue size - " + queue.size() + " channels");

                    for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
                        new Thread(() -> {

                            Object o = new Object();

                            String channelId;
                            while ((channelId = queue.poll()) != null) {
                                try {
                                    CompletableFuture<?> future = PubSubHelper.subscribePubSub(channelId);

                                    if (future == null)
                                        continue;

                                    future.whenComplete((resp, throwable) -> {
                                        synchronized (o) {
                                            o.notify();
                                        }
                                    });

                                    synchronized (o) {
                                        o.wait();
                                    }

                                } catch (Exception e) {
                                    ExceptionHandler.handle(e);
                                }
                            }
                        }, "PubSub-" + i).start();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(90));

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {

                    s.createNativeQuery("SELECT channel_id.channel FROM " +
                                    "(SELECT DISTINCT channel FROM users_subscribed UNION SELECT id FROM unauthenticated_subscriptions WHERE subscribed_at > :unauthSubbed) " +
                                    "channel_id LEFT JOIN pubsub on pubsub.id = channel_id.channel " +
                                    "WHERE pubsub.id IS NULL", String.class)
                            .setParameter("unauthSubbed", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.SUBSCRIPTIONS_EXPIRY))
                            .getResultStream()
                            .parallel()
                            .filter(ChannelHelpers::isValidId)
                            .forEach(id -> Multithreading.runAsyncLimitedPubSub(() -> {
                                try (StatelessSession sess = DatabaseSessionFactory.createStatelessSession()) {
                                    var pubsub = new PubSub(id, -1);
                                    var tr = sess.beginTransaction();
                                    sess.insert(pubsub);
                                    tr.commit();
                                }
                            }));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.DAYS.toMillis(1));

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

                    var subQuery = pvQuery.subquery(String.class);
                    var subRoot = subQuery.from(me.kavin.piped.utils.obj.db.Playlist.class);

                    subQuery.select(subRoot.join("videos").get("id")).distinct(true);

                    pvQuery.where(cb.not(pvRoot.get("id").in(subQuery)));

                    var tr = s.beginTransaction();
                    s.createMutationQuery(pvQuery).executeUpdate();
                    tr.commit();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(60));

    }
}
