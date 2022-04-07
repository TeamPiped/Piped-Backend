package me.kavin.piped.utils;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.Playlist;
import me.kavin.piped.utils.obj.db.PlaylistVideo;
import me.kavin.piped.utils.obj.db.PubSub;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class DatabaseSessionFactory {

    private static final SessionFactory sessionFactory;

    static {

        final Configuration configuration = new Configuration();

        Constants.hibernateProperties.forEach((key, value) -> configuration.setProperty(key, value));
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.configure();

        sessionFactory = configuration.addAnnotatedClass(User.class).addAnnotatedClass(Channel.class)
                .addAnnotatedClass(Video.class).addAnnotatedClass(PubSub.class).addAnnotatedClass(Playlist.class)
                .addAnnotatedClass(PlaylistVideo.class).buildSessionFactory();
    }

    public static final Session createSession() {
        return sessionFactory.openSession();
    }
}
