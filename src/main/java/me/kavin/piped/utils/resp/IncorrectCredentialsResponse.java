package me.kavin.piped.utils.resp;

import me.kavin.piped.utils.IStatusCode;

public class IncorrectCredentialsResponse implements IStatusCode {

    public String error = "The username or password you have entered is incorrect.";

    @Override
    public int getStatusCode() {
        return 401;
    }
}
