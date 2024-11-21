package me.kavin.piped.server.handlers.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import io.activej.http.HttpResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DatabaseHelper;
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.RequestUtils;
import me.kavin.piped.utils.obj.OidcProvider;
import me.kavin.piped.utils.obj.db.OidcData;
import me.kavin.piped.utils.obj.db.OidcUserData;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.resp.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
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
        if (hash.isBlank()) {
            return false;
        }
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

    public static HttpResponse oidcLoginRequest(OidcProvider provider, String redirectUri) throws Exception {
        if (StringUtils.isBlank(redirectUri)) {
            return HttpResponse.ofCode(400).withHtml("redirect is a required parameter");
        }

        URI callback = new URI(Constants.PUBLIC_URL + "/oidc/" + provider.name + "/callback");
        CodeVerifier codeVerifier = new CodeVerifier();
        OidcData data = new OidcData(redirectUri, codeVerifier);
        String state = data.getState();

        DatabaseHelper.setOidcData(data);

        AuthenticationRequest oidcRequest = new AuthenticationRequest.Builder(
                new ResponseType("code"),
                new Scope("openid"),
                provider.clientID, callback
        )
                .endpointURI(provider.authUri)
                .codeChallenge(codeVerifier, CodeChallengeMethod.S256)
                .state(new State(state))
                .nonce(data.getOidNonce()).build();

        if (redirectUri.equals(Constants.FRONTEND_URL + "/login")) {
            return HttpResponse.redirect302(oidcRequest.toURI().toString());
        }
        return HttpResponse.ok200().withHtml(
                "<!DOCTYPE html><html style=\"color-scheme: dark light;\"><body>" +
                        "<h3>Warning:</h3> You are trying to give <pre style=\"font-size: 1.2rem;\">" +
                        redirectUri +
                        "</pre> access to your Piped account. If you wish to continue click " +
                        "<a style=\"text-decoration: underline;color: inherit;\"href=\"" +
                        oidcRequest.toURI().toString() +
                        "\">here</a></body></html>");
    }

    public static HttpResponse oidcLoginCallback(OidcProvider provider, URI requestUri) throws Exception {
        AuthenticationSuccessResponse authResponse = parseOidcUri(requestUri);

        OidcData data = DatabaseHelper.getOidcData(authResponse.getState().toString());
        if (data == null) {
            return HttpResponse.ofCode(400).withHtml(
                    "Your oidc provider sent invalid state data. Try again or contact your oidc admin"
            );
        }

        URI callback = new URI(Constants.PUBLIC_URL + "/oidc/" + provider.name + "/callback");
        AuthorizationCode code = authResponse.getAuthorizationCode();

        if (code == null) {
            return HttpResponse.ofCode(400).withHtml(
                    "Your oidc provider sent an invalid code. Try again or contact your oidc admin"
            );
        }

        AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, callback, data.getOidVerifier());

        ClientAuthentication clientAuth = new ClientSecretBasic(provider.clientID, provider.clientSecret);
        TokenRequest tokenReq = new TokenRequest.Builder(provider.tokenUri, clientAuth, codeGrant).build();

        com.nimbusds.oauth2.sdk.http.HTTPResponse tokenResponseText = tokenReq.toHTTPRequest().send();
        OIDCTokenResponse tokenResponse = (OIDCTokenResponse) OIDCTokenResponseParser.parse(tokenResponseText);

        if (!tokenResponse.indicatesSuccess()) {
            TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
            return HttpResponse.ofCode(500).withHtml("Failure while trying to request token:\n\n" + errorResponse.getErrorObject().getDescription());
        }

        OIDCTokenResponse successResponse = tokenResponse.toSuccessResponse();

        JWT idToken = JWTParser.parse(successResponse.getOIDCTokens().getIDTokenString());

        try {
            provider.validator.validate(idToken, data.getOidNonce());
        } catch (BadJOSEException e) {
            System.err.println("Invalid token received: " + e);
            return HttpResponse.ofCode(400).withHtml("Received a bad token. Please try again");
        } catch (JOSEException e) {
            System.err.println("Token processing error: " + e);
            return HttpResponse.ofCode(500).withHtml("Internal processing error. Please try again");
        }

        UserInfoRequest ur = new UserInfoRequest(provider.userinfoUri, successResponse.getOIDCTokens().getBearerAccessToken());
        UserInfoResponse userInfoResponse = UserInfoResponse.parse(ur.toHTTPRequest().send());

        if (!userInfoResponse.indicatesSuccess()) {
            return HttpResponse.ofCode(500).withHtml(
                    "The userinfo endpoint returned an error. Please try again or contact your oidc admin\n\n" +
                            userInfoResponse.toErrorResponse().getErrorObject().getDescription());
        }

        UserInfo userInfo = userInfoResponse.toSuccessResponse().getUserInfo();

        String sub = userInfo.getSubject().toString();
        String sessionId;
        try (Session s = DatabaseSessionFactory.createSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<OidcUserData> cr = cb.createQuery(OidcUserData.class);
            Root<OidcUserData> root = cr.from(OidcUserData.class);

            cr.select(root).where(root.get("sub").in(sub));

            OidcUserData dbuser = s.createQuery(cr).uniqueResult();

            if (dbuser != null) {
                sessionId = dbuser.getUser().getSessionId();
            } else {
                OidcUserData newUser = new OidcUserData(sub, RandomStringUtils.randomAlphabetic(24), provider.name);

                var tr = s.beginTransaction();
                s.persist(newUser);
                tr.commit();

                sessionId = newUser.getUser().getSessionId();
            }
        }
        return HttpResponse.redirect302(data.data + "?session=" + sessionId);
    }

    public static HttpResponse oidcDeleteRequest(String session, String redirect) throws Exception {

        if (StringUtils.isBlank(session)) {
            return HttpResponse.ofCode(400).withHtml("session is a required parameter");
        }

        if (StringUtils.isBlank(redirect)) {
            return HttpResponse.ofCode(400).withHtml("redirect is a required parameter");
        }

        OidcProvider provider = null;

        try (Session s = DatabaseSessionFactory.createSession()) {
            User user = DatabaseHelper.getUserFromSession(session);

            if (user == null) {
                return HttpResponse.ofCode(400).withHtml("User not found");
            }

            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<OidcUserData> cr = cb.createQuery(OidcUserData.class);
            Root<OidcUserData> root = cr.from(OidcUserData.class);
            cr.select(root).where(cb.equal(root.get("user"), user));

            OidcUserData oidcUserData = s.createQuery(cr).uniqueResult();

            if (oidcUserData == null) {
                return HttpResponse.ofCode(400).withHtml("User doesn't have an oidc account");
            }

            for (OidcProvider oidcProvider : Constants.OIDC_PROVIDERS) {
                if (oidcProvider.name.equals(oidcUserData.getProvider())) {
                    provider = oidcProvider;
                    break;
                }
            }
        }

        if (provider == null) {
            return HttpResponse.ofCode(400).withHtml("Invalid user");
        }

        CodeVerifier pkceVerifier = new CodeVerifier();

        URI callback = URI.create(String.format("%s/oidc/%s/delete", Constants.PUBLIC_URL, provider.name));
        OidcData data = new OidcData(session + "|" + redirect, pkceVerifier);
        String state = data.getState();

        DatabaseHelper.setOidcData(data);

        com.nimbusds.openid.connect.sdk.AuthenticationRequest.Builder oidcRequestBuilder = new AuthenticationRequest.Builder(
                new ResponseType("code"),
                new Scope("openid"), provider.clientID, callback
        )
                .endpointURI(provider.authUri)
                .codeChallenge(pkceVerifier, CodeChallengeMethod.S256)
                .state(new State(state))
                .nonce(data.getOidNonce());

                if (provider.sendMaxAge) {
                    // This parameter is optional and the idp doesn't have to honor it.
                    oidcRequestBuilder.maxAge(0);
                }

        return HttpResponse.redirect302(oidcRequestBuilder.build().toURI().toString());
    }

    public static HttpResponse oidcDeleteCallback(OidcProvider provider, URI requestUri) throws Exception {

        AuthenticationSuccessResponse sr = parseOidcUri(requestUri);

        OidcData data = DatabaseHelper.getOidcData(sr.getState().toString());

        if (data == null) {
            return HttpResponse.ofCode(400).withHtml(
                    "Your oidc provider sent invalid state data. Try again or contact your oidc admin"
            );
        }

        String redirect = data.data.split("\\|")[1];
        String session = data.data.split("\\|")[0];

        URI callback = new URI(Constants.PUBLIC_URL + "/oidc/" + provider.name + "/delete");
        AuthorizationCode code = sr.getAuthorizationCode();

        if (code == null) {
            return HttpResponse.ofCode(400).withHtml(
                    "Your oidc provider sent an invalid code. Try again or contact your oidc admin"
            );
        }

        AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, callback, data.getOidVerifier());

        ClientAuthentication clientAuth = new ClientSecretBasic(provider.clientID, provider.clientSecret);

        TokenRequest tokenRequest = new TokenRequest.Builder(provider.tokenUri, clientAuth, codeGrant).build();
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());

        if (!tokenResponse.indicatesSuccess()) {
            TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
            return HttpResponse.ofCode(500).withHtml("Failure while trying to request token:\n\n" + errorResponse.getErrorObject().getDescription());
        }

        OIDCTokenResponse successResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();

        JWT idToken = JWTParser.parse(successResponse.getOIDCTokens().getIDTokenString());

        IDTokenClaimsSet claims;
        try {
            claims = provider.validator.validate(idToken, data.getOidNonce());
        } catch (BadJOSEException e) {
            System.err.println("Invalid token received: " + e);
            return HttpResponse.ofCode(400).withHtml("Received a bad token. Please try again");
        } catch (JOSEException e) {
            System.err.println("Token processing error: " + e);
            return HttpResponse.ofCode(500).withHtml("Internal processing error. Please try again");
        }

        if (provider.sendMaxAge) {
          Long authTime = (Long) claims.getNumberClaim("auth_time");

          if (authTime == null) {
              return HttpResponse.ofCode(400).withHtml("Couldn't get the `auth_time` claim from the provided id token");
          }

          if (authTime <= data.start) {
              return HttpResponse.ofCode(500).withHtml(
                      "Your oidc provider didn't verify your identity. Please try again or contact your oidc admin."
              );
          }
        }

        try (Session s = DatabaseSessionFactory.createSession()) {
            var tr = s.beginTransaction();

            User toDelete = DatabaseHelper.getUserFromSession(session);

            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<OidcUserData> cr = cb.createQuery(OidcUserData.class);
            Root<OidcUserData> root = cr.from(OidcUserData.class);

            cr.select(root).where(cb.equal(root.get("user"), toDelete));

            s.remove(s.createQuery(cr).uniqueResult());
            tr.commit();
        }

        return HttpResponse.redirect302(redirect + "?deleted=true");
    }

    public static byte[] deleteUserResponse(String session, String pass) throws IOException {
        if (StringUtils.isBlank(session))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("session is a required parameter"));

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

    private static AuthenticationSuccessResponse parseOidcUri(URI uri) throws Exception {
        AuthenticationResponse response = AuthenticationResponseParser.parse(uri);

        if (response instanceof AuthenticationErrorResponse) {
            System.err.println(response.toErrorResponse().getErrorObject());
            throw new Exception(response.toErrorResponse().getErrorObject().toString());
        }
        return response.toSuccessResponse();
    }
}
