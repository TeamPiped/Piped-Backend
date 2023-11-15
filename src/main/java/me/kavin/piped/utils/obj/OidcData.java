package me.kavin.piped.utils.obj;

import com.nimbusds.openid.connect.sdk.Nonce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class OidcData {
    public final Nonce nonce;

    public String data;

    public OidcData(String data) {
        this.nonce = new Nonce();
        this.data = data;
    }

    public boolean isInvalidNonce(String nonce) {
        return !nonce.equals(this.nonce.toString());
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
