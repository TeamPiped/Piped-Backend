package me.kavin.piped.utils.resp;

import me.kavin.piped.utils.IStatusCode;

public class AuthenticationFailureResponse implements IStatusCode {

    public String error = "An invalid Session ID was provided.";

    @Override
    public int getStatusCode() {
        return 401;
    }
}
