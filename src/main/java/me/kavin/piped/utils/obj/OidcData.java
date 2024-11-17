package me.kavin.piped.utils.obj;

import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

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
        return this.nonce.getValue().equals(nonce);
    }

    public String getState() {
        String value = nonce + data;

        byte[] hash = DigestUtils.sha256(value);
        return Base64.encodeBase64String(hash);
    }
}
