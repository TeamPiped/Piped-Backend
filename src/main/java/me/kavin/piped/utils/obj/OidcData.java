package me.kavin.piped.utils.obj;

import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class OidcData {
    public final Nonce nonce;
    public final CodeVerifier pkceVerifier;
    public final String data;

    public OidcData(String data, CodeVerifier pkceVerifier) {
        this.nonce = new Nonce();
        this.pkceVerifier = pkceVerifier;
        this.data = data;
    }

    public boolean validateNonce(String nonce) {
        return this.nonce.toString().equals(nonce);
    }

    public String getState() {
        String value = nonce + data;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
