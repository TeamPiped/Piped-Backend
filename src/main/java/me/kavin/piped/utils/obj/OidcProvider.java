package me.kavin.piped.utils.obj;

import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;

import java.net.URI;
import java.net.URISyntaxException;

public class OidcProvider {
    public String name;
    public ClientID clientID;
    public Secret clientSecret;
    public URI authUri;
    public URI tokenUri;
    public URI userinfoUri;

    public OidcProvider(String name, String clientID, String clientSecret, String authUri, String tokenUri, String userinfoUri) {
        this.name = name;
        this.clientID = new ClientID(clientID);
        this.clientSecret = new Secret(clientSecret);
        try {
            this.authUri = new URI(authUri);
            this.tokenUri = new URI(tokenUri);
            this.userinfoUri = new URI(userinfoUri);
        } catch (URISyntaxException e) {
            System.err.println("Malformed URI for oidc provider '" + name + "' found.");
            System.exit(1);
        }
    }
}
