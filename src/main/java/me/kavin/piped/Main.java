package me.kavin.piped;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;

import io.activej.inject.Injector;
import me.kavin.piped.utils.DatabaseHelper;
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.DownloaderImpl;
import me.kavin.piped.utils.ResponseHelper;

public class Main {

    public static void main(String[] args) throws Exception {

        NewPipe.init(new DownloaderImpl(), new Localization("en", "US"));

        Injector.useSpecializer();

        new Thread(() -> {
            DatabaseSessionFactory.createSession().close();
        }).start();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Session s = DatabaseSessionFactory.createSession();

                    for (String channelId : DatabaseHelper.getGlobalSubscribedChannelIds(s))
                        ResponseHelper.subscribePubSub(channelId);

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
                            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10));

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
