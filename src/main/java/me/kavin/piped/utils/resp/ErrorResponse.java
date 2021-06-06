package me.kavin.piped.utils.resp;

public class ErrorResponse {

    public final String error, message;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }
}
