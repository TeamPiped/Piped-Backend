package me.kavin.piped;

import io.activej.inject.Injector;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.DownloaderImpl;
import me.kavin.piped.utils.Multithreading;
import me.kavin.piped.utils.ResponseHelper;
import me.kavin.piped.utils.obj.db.PubSub;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;

import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {

        NewPipe.init(new DownloaderImpl(), new Localization("en", "US"));

        Injector.useSpecializer();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Session s = DatabaseSessionFactory.createSession();

                    List<PubSub> pubSubList = s.createNativeQuery(
                            "select distinct pubsub.* from pubsub inner join users_subscribed on pubsub.id = users_subscribed.channel where pubsub.subbed_at < :time",
                            PubSub.class).setParameter("time", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))
                            .getResultList();

                    Collections.shuffle(pubSubList);

                    for (PubSub pubsub : pubSubList)
                        if (pubsub != null)
                            Multithreading.runAsyncLimitedPubSub(() -> {
                                Session sess = DatabaseSessionFactory.createSession();
                                try {
                                    ResponseHelper.subscribePubSub(pubsub.getId(), sess);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                sess.close();
                            });

                    s.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(90));

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Session s = DatabaseSessionFactory.createSession();

                    Transaction tr = s.getTransaction();

                    tr.begin();

                    Query<?> query = s.createQuery("delete from Video where uploaded < :time").setParameter("time",
                            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION));

                    System.out.println(String.format("Cleanup: Removed %o old videos", query.executeUpdate()));

                    tr.commit();

                    s.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(60));

        new ServerLauncher().launch(args);

    }
}
