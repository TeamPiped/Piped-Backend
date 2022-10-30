package me.kavin.piped.utils.resp;

import me.kavin.piped.utils.IStatusCode;

public class DisabledRegistrationResponse implements IStatusCode {

    public String error = "This instance has registrations disabled.";

    @Override
    public int getStatusCode() {
        return 400;
    }
}
