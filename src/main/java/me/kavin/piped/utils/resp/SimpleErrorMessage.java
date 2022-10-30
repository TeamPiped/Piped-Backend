package me.kavin.piped.utils.resp;

import me.kavin.piped.utils.IStatusCode;

public record SimpleErrorMessage(String error) implements IStatusCode {
    @Override
    public int getStatusCode() {
        return 500;
    }
}
