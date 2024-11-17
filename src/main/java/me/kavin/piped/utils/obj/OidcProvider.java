package me.kavin.piped.utils.obj;

import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;

import java.io.IOException;
import java.net.URI;

public class OidcProvider {

    public final String name;
    public final ClientID clientID;
    public final Secret clientSecret;
    public final URI authUri;
    public final URI tokenUri;
    public URI userinfoUri;
    public IDTokenValidator validator;

    public OidcProvider(String name, String clientId, String clientSecret, String issuer) throws GeneralException, IOException {
        this.name = name;
        this.clientID = new ClientID(clientId);
        this.clientSecret = new Secret(clientSecret);

        Issuer iss = new Issuer(issuer);
        OIDCProviderMetadata providerData = OIDCProviderMetadata.resolve(iss);
        this.authUri = providerData.getAuthorizationEndpointURI();
        this.tokenUri = providerData.getTokenEndpointURI();
        this.userinfoUri = providerData.getUserInfoEndpointURI();
        this.validator = new IDTokenValidator(iss, this.clientID, providerData.getIDTokenJWSAlgs().getFirst(), providerData.getJWKSetURI().toURL());
    }
}
