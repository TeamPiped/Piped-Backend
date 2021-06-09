package me.kavin.piped.utils;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.obj.db.Video;

public class DatabaseSessionFactory {

    private static final SessionFactory sessionFactory;

    static {

        final Configuration configuration = new Configuration();

        configuration.setProperty("hibernate.connection.url", "jdbc:postgresql://");
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.connection.username", "piped");
        configuration.setProperty("hibernate.connection.password", "@8LQuf7JUabCker$zQYS");
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.configure();

        sessionFactory = configuration.addAnnotatedClass(User.class).addAnnotatedClass(Video.class)
                .buildSessionFactory();
    }

    public static final Session createSession() {
        return sessionFactory.openSession();
    }
}
