package me.kavin.piped.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentry.Sentry;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class ExceptionHandler {

    public static Exception handle(Exception e) {
        return handle(e, null);
    }

    public static Exception handle(Exception e, String path) {

        if (e.getCause() != null && (e instanceof ExecutionException || e instanceof CompletionException))
            e = (Exception) e.getCause();

        if (e instanceof ContentNotAvailableException || e instanceof ErrorResponse)
            return e;

        if ((e instanceof ExtractionException extractionException && extractionException.getMessage().contains("No service can handle the url")))
            try {
                return new ErrorResponse(new InvalidRequestResponse("Invalid parameter provided, unknown service"), extractionException);
            } catch (JsonProcessingException jsonProcessingException) {
                throw new RuntimeException(jsonProcessingException);
            }

        Sentry.captureException(e);
        if (Constants.SENTRY_DSN.isEmpty()) {
            if (path != null)
                System.err.println("An error occoured in the path: " + path);
            e.printStackTrace();
        }

        return e;
    }

    public static void throwErrorResponse(IStatusCode statusObj) {
        try {
            ExceptionUtils.rethrow(new ErrorResponse(statusObj));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void throwErrorResponse(int code, Object content) {
        try {
            ExceptionUtils.rethrow(new ErrorResponse(code, content));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
