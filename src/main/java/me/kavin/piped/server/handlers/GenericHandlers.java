package me.kavin.piped.server.handlers;

import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DatabaseSessionFactory;
import org.hibernate.StatelessSession;

import static me.kavin.piped.consts.Constants.mapper;

public class GenericHandlers {

    public static byte[] configResponse() throws Exception {
        return mapper.writeValueAsBytes(Constants.frontendProperties);
    }

    public static String registeredBadgeRedirect() {
        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            long registered = s.createQuery("select count(*) from User", Long.class).uniqueResult();

            return String.format("https://img.shields.io/badge/Registered%%20Users-%s-blue", registered);
        }
    }
}
