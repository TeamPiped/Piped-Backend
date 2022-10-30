package me.kavin.piped.utils.resp;

import me.kavin.piped.utils.IStatusCode;

public class CompromisedPasswordResponse implements IStatusCode {

    public String error = "The password you have entered has already been compromised.";

    @Override
    public int getStatusCode() {
        return 400;
    }
}
