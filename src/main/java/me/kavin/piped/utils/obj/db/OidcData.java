package me.kavin.piped.utils.obj.db;

import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;

import java.io.Serializable;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import jakarta.persistence.*;

@Entity
@Table(name = "oidc_logins")
public class OidcData implements Serializable {

  @Column(name = "nonce", unique = true, length = 256)
  @Id
  public String nonce;

  @Column(name = "verifier", length = 128)
  public String verifierSecret;

  @Column(name = "data")
  public String data;

  @Column(name = "state")
  public String state;

  @Column(name = "start")
  public long start;

  public OidcData(String data, CodeVerifier pkceVerifier) {
    this.nonce = new Nonce().toString();
    this.verifierSecret = pkceVerifier.getValue();
    this.data = data;
    this.start = System.currentTimeMillis() / 1000L;
    this.state = getState();
  }

  public OidcData() {
  }

  public boolean validateNonce(String nonce) {
    return this.nonce.equals(nonce);
  }

  public String getState() {
    String value = this.nonce + this.data;

    byte[] hash = DigestUtils.sha256(value);
    return Base64.encodeBase64String(hash);
  }

  public Nonce getOidNonce(){
    return new Nonce(this.nonce);
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }

  public CodeVerifier getOidVerifier(){
    return new CodeVerifier(this.verifierSecret);
  }

  public void setVerifier(String verifier) {
    this.verifierSecret = verifier;
  }

  public void setData(String data) {
    this.data = data;
  }

  public void setState(String state) {
    this.state = state;
  }
}
