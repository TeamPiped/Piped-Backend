package me.kavin.piped.utils;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.Serial;

import static me.kavin.piped.consts.Constants.mapper;

public class ErrorResponse extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    private final byte[] content;

    public ErrorResponse(int code, byte[] content) {
        this.code = code;
        this.content = content;
    }

    public ErrorResponse(IStatusCode statusObj) throws JsonProcessingException {
        this.code = statusObj.getStatusCode();
        this.content = mapper.writeValueAsBytes(statusObj);
    }

    public ErrorResponse(IStatusCode statusObj, Throwable throwable) throws JsonProcessingException {
        super(throwable);
        this.code = statusObj.getStatusCode();
        this.content = mapper.writeValueAsBytes(statusObj);
    }

    public ErrorResponse(int code, Object content) throws JsonProcessingException {
        this.code = code;
        this.content = mapper.writeValueAsBytes(content);
    }

    public ErrorResponse(int code, Object content, Throwable throwable) throws JsonProcessingException {
        super(throwable);
        this.code = code;
        this.content = mapper.writeValueAsBytes(content);
    }

    public int getCode() {
        return code;
    }

    public byte[] getContent() {
        return content;
    }
}
