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
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo; // Added import
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo; // Added import
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs; // Added import
import org.schabi.newpipe.extractor.exceptions.ExtractionException; // Added import
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler; // Added import
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem; // Added import
import rocks.kavin.reqwest4j.ReqwestUtils;

import java.io.IOException; // Added import
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
        if (!StringUtils.isEmpty(Constants.BG_HELPER_URL))
            YoutubeStreamExtractor.setPoTokenProvider(new BgPoTokenProvider(Constants.BG_HELPER_URL));
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

        Multithreading.runAsync(() -> Thread.ofVirtual().start(new SyncRunner(
                new OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build(),
                MATRIX_SERVER,
                MatrixHelper.MATRIX_TOKEN)
        ));

        new Timer("ThrottlingCache-Clear", true).scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.printf("ThrottlingCache: %o entries%n", YoutubeJavaScriptPlayerManager.getThrottlingParametersCacheSize());
                YoutubeJavaScriptPlayerManager.clearThrottlingParametersCache();
            }
        }, TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(60)); // Start after 1 min, repeat hourly

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

        if (Constants.DISABLE_TIMERS) {
            System.out.println("Timers are disabled."); // Add log message
        } else {
            // --- Existing Timer Tasks ---
            // PubSub Resubscription Timer
            new Timer("PubSub-Resubscribe", true).scheduleAtFixedRate(new TimerTask() {
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
            }, TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(90)); // Start after 5 min, repeat every 90 min

            // PubSub Missing Channel Check Timer
            new Timer("PubSub-MissingCheck", true).scheduleAtFixedRate(new TimerTask() {
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
            }, TimeUnit.MINUTES.toMillis(10), TimeUnit.DAYS.toMillis(1)); // Start after 10 min, repeat daily

            // Video Cleanup Timer
            new Timer("Video-Cleanup", true).scheduleAtFixedRate(new TimerTask() {
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
            }, TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(60)); // Start after 15 min, repeat hourly

            // Playlist Video Cleanup Timer
            new Timer("PlaylistVideo-Cleanup", true).scheduleAtFixedRate(new TimerTask() {
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
            }, TimeUnit.MINUTES.toMillis(20), TimeUnit.MINUTES.toMillis(60)); // Start after 20 min, repeat hourly


            // --- NEW Feed Polling Timer ---
            if (Constants.ENABLE_FEED_POLLING) {
                System.out.println("Feed polling enabled. Interval: " + Constants.POLLING_INTERVAL_MINUTES + " minutes.");
                new Timer("Feed-Polling", true).scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        System.out.println("Starting feed polling cycle...");
                        long startTime = System.currentTimeMillis();
                        Set<String> uniqueChannelIds;

                        // 1. Get all unique subscribed channel IDs
                        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
                            long expiryTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.SUBSCRIPTIONS_EXPIRY);
                            // Combine authenticated and unauthenticated subscriptions
                            List<String> subscribedIds = s.createNativeQuery(
                                            "SELECT DISTINCT channel FROM users_subscribed " +
                                            "UNION " +
                                            "SELECT id FROM unauthenticated_subscriptions WHERE subscribed_at > :unauthSubbed",
                                            String.class)
                                    .setParameter("unauthSubbed", expiryTimestamp)
                                    .list();
                            uniqueChannelIds = new HashSet<>(subscribedIds); // Use HashSet for efficient distinctness
                        } catch (Exception e) {
                            System.err.println("Error fetching subscribed channels for polling:");
                            ExceptionHandler.handle(e);
                            return; // Stop this cycle if we can't get channels
                        }

                        if (uniqueChannelIds.isEmpty()) {
                            System.out.println("No channels to poll.");
                            return;
                        }

                        System.out.println("Polling " + uniqueChannelIds.size() + " unique channels.");

                        // 2. Process each channel in parallel
                        List<CompletableFuture<Void>> futures = uniqueChannelIds.stream()
                                .map(channelId -> CompletableFuture.runAsync(() -> pollChannel(channelId), Multithreading.getCachedExecutor()))
                                .toList();

                        // Wait for all polling tasks to complete for this cycle
                        try {
                            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(); // Wait for completion
                            long duration = System.currentTimeMillis() - startTime;
                            System.out.println("Feed polling cycle finished in " + duration + " ms.");
                        } catch (Exception e) {
                             System.err.println("Error waiting for polling tasks to complete:");
                             ExceptionHandler.handle(e);
                        }
                    }
                }, TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(Constants.POLLING_INTERVAL_MINUTES)); // Start after 1 min, repeat per config
            } else {
                 System.out.println("Feed polling is disabled via configuration.");
            }
        } // End of !DISABLE_TIMERS block
    } // End of main method

    /**
     * Polls a single channel for recent videos and updates the database.
     * Designed to be run asynchronously.
     * @param channelId The YouTube channel ID to poll.
     */
    private static void pollChannel(String channelId) {
        if (!ChannelHelpers.isValidId(channelId)) {
            // System.err.println("Skipping invalid channel ID during poll: " + channelId);
            return;
        }

        // System.out.println("Polling channel: " + channelId); // Optional: verbose logging
        long pollStartTime = System.currentTimeMillis();
        int videosProcessed = 0;
        // int videosAdded = 0; // Hard to track accurately without more logic

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            // Fetch the Channel entity first - needed for foreign key in Video table
            me.kavin.piped.utils.obj.db.Channel channelEntity = DatabaseHelper.getChannelFromId(s, channelId);
            if (channelEntity == null) {
                // Attempt to save the channel if it's missing (might happen with unauthenticated subs)
                System.out.println("Channel " + channelId + " not found in DB, attempting to save.");
                channelEntity = DatabaseHelper.saveChannel(channelId); // This might be slow, consider alternatives if performance is critical
                if (channelEntity == null) {
                     System.err.println("Failed to find or save channel " + channelId + " for polling. Skipping.");
                     return;
                }
            }

            // Fetch only the videos tab for efficiency
            ChannelInfo channelInfo = ChannelInfo.getInfo("https://youtube.com/channel/" + channelId);
            ListLinkHandler videosTabHandler = channelInfo.getTabs()
                    .stream()
                    .filter(tab -> tab.getContentFilters().contains(ChannelTabs.VIDEOS))
                    .findFirst()
                    .orElse(null);

            if (videosTabHandler == null) {
                System.err.println("Could not find videos tab for channel: " + channelId);
                return;
            }

            // Fetch the first page of the videos tab
            ChannelTabInfo tabInfo = ChannelTabInfo.getInfo(YOUTUBE_SERVICE, videosTabHandler);
            List<StreamInfoItem> items = tabInfo.getRelatedItems()
                    .stream()
                    .filter(StreamInfoItem.class::isInstance)
                    .map(StreamInfoItem.class::cast)
                    .limit(Constants.POLLING_FETCH_LIMIT_PER_CHANNEL) // Limit fetched items
                    .toList();

            // Process the fetched items
            for (StreamInfoItem item : items) {
                // Use the efficient helper that avoids re-fetching
                VideoHelpers.handleNewVideo(item, System.currentTimeMillis(), channelEntity);
                videosProcessed++;
                // Note: We can't easily tell if it was *added* vs updated here without more complex logic
            }

        } catch (ExtractionException | IOException e) {
             System.err.println("Error polling channel " + channelId + ": " + e.getMessage());
             // Don't print full stack trace for common extraction errors unless debugging
             // ExceptionHandler.handle(e);
        } catch (Exception e) {
            // Catch unexpected errors
            System.err.println("Unexpected error polling channel " + channelId + ":");
            ExceptionHandler.handle(e);
        } finally {
             long pollDuration = System.currentTimeMillis() - pollStartTime;
             // Optional: Log duration per channel if needed for performance tuning
             // System.out.println("Finished polling channel " + channelId + " in " + pollDuration + "ms. Processed: " + videosProcessed);
        }
    }
} // End of Main class