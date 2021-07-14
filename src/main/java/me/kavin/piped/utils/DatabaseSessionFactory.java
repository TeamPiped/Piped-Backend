package me.kavin.piped.utils;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.obj.db.Channel;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;

public class DatabaseSessionFactory {

    private static final SessionFactory sessionFactory;

    static {

        final Configuration configuration = new Configuration();

        Constants.hibernateProperties.forEach((key, value) -> configuration.setProperty(key, value));
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.configure();

        sessionFactory = configuration.addAnnotatedClass(User.class).addAnnotatedClass(Video.class)
                .addAnnotatedClass(Channel.class).buildSessionFactory();
    }

    public static final Session createSession() {
        return sessionFactory.openSession();
    }
}
