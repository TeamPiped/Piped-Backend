package me.kavin.piped.utils.resp;

import me.kavin.piped.utils.IStatusCode;

public class InvalidRequestResponse implements IStatusCode {

    public String error;

    public InvalidRequestResponse(String error) {
        this.error = error;
    }

    public InvalidRequestResponse() {
        this.error = "Invalid request sent.";
    }

    @Override
    public int getStatusCode() {
        return 400;
    }
}
