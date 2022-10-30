package me.kavin.piped.utils.resp;

import me.kavin.piped.utils.IStatusCode;

public class AlreadyRegisteredResponse implements IStatusCode {

    public String error = "The username you have used is already taken.";

    @Override
    public int getStatusCode() {
        return 400;
    }
}
