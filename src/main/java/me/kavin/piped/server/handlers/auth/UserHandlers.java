package me.kavin.piped.server.handlers.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DatabaseHelper;
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.RequestUtils;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.resp.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static me.kavin.piped.consts.Constants.mapper;

public class UserHandlers {
    private static final Argon2PasswordEncoder argon2PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private static final BCryptPasswordEncoder bcryptPasswordEncoder = new BCryptPasswordEncoder();

    public static byte[] registerResponse(String user, String pass) throws Exception {

        if (Constants.DISABLE_REGISTRATION)
            ExceptionHandler.throwErrorResponse(new DisabledRegistrationResponse());

        if (StringUtils.isBlank(user) || StringUtils.isBlank(pass))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse());

        if (user.length() > 24)
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("The username must be less than 24 characters"));

        user = user.toLowerCase();

        try (Session s = DatabaseSessionFactory.createSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<User> cr = cb.createQuery(User.class);
            Root<User> root = cr.from(User.class);
            cr.select(root).where(cb.equal(root.get("username"), user));
            boolean registered = s.createQuery(cr).uniqueResult() != null;

            if (registered)
                ExceptionHandler.throwErrorResponse(new AlreadyRegisteredResponse());

            if (Constants.COMPROMISED_PASSWORD_CHECK) {
                String sha1Hash = DigestUtils.sha1Hex(pass).toUpperCase();
                String prefix = sha1Hash.substring(0, 5);
                String suffix = sha1Hash.substring(5);
                String[] entries = RequestUtils
                        .sendGet("https://api.pwnedpasswords.com/range/" + prefix, "github.com/TeamPiped/Piped-Backend")
                        .thenApplyAsync(str -> str.split("\n"))
                        .get();
                for (String entry : entries)
                    if (StringUtils.substringBefore(entry, ":").equals(suffix))
                        ExceptionHandler.throwErrorResponse(new CompromisedPasswordResponse());
            }

            User newuser = new User(user, argon2PasswordEncoder.encode(pass), Set.of());

            var tr = s.beginTransaction();
            s.persist(newuser);
            tr.commit();


            return mapper.writeValueAsBytes(new LoginResponse(newuser.getSessionId()));
        }
    }

    private static boolean hashMatch(String hash, String pass) {
        return hash.startsWith("$argon2") ?
                argon2PasswordEncoder.matches(pass, hash) :
                bcryptPasswordEncoder.matches(pass, hash);
    }

    public static byte[] loginResponse(String user, String pass)
            throws IOException {

        if (user == null || pass == null)
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("username and password are required parameters"));

        user = user.toLowerCase();

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<User> cr = cb.createQuery(User.class);
            Root<User> root = cr.from(User.class);
            cr.select(root).where(root.get("username").in(user));

            User dbuser = s.createQuery(cr).uniqueResult();

            if (dbuser != null) {
                String hash = dbuser.getPassword();
                if (hashMatch(hash, pass)) {
                    return mapper.writeValueAsBytes(new LoginResponse(dbuser.getSessionId()));
                }
            }

            ExceptionHandler.throwErrorResponse(new IncorrectCredentialsResponse());
            return null;
        }
    }

    public static byte[] deleteUserResponse(String session, String pass) throws IOException {

        if (StringUtils.isBlank(session) || StringUtils.isBlank(pass))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session and password are required parameters"));

        try (Session s = DatabaseSessionFactory.createSession()) {
            User user = DatabaseHelper.getUserFromSession(session);

            if (user == null)
                ExceptionHandler.throwErrorResponse(new AuthenticationFailureResponse());

            String hash = user.getPassword();

            if (!hashMatch(hash, pass))
                ExceptionHandler.throwErrorResponse(new IncorrectCredentialsResponse());

            var tr = s.beginTransaction();
            s.remove(user);
            tr.commit();

            return mapper.writeValueAsBytes(new DeleteUserResponse(user.getUsername()));
        }
    }

    public static byte[] logoutResponse(String session) throws JsonProcessingException {

        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

        try (StatelessSession s = DatabaseSessionFactory.createStatelessSession()) {
            var tr = s.beginTransaction();
            if (s.createMutationQuery("UPDATE User user SET user.sessionId = :newSessionId where user.sessionId = :sessionId")
                    .setParameter("sessionId", session).setParameter("newSessionId", String.valueOf(UUID.randomUUID()))
                    .executeUpdate() > 0) {
                tr.commit();
                return Constants.mapper.writeValueAsBytes(new AcceptedResponse());
            } else
                tr.rollback();
        }

        return Constants.mapper.writeValueAsBytes(new AuthenticationFailureResponse());
    }
}
