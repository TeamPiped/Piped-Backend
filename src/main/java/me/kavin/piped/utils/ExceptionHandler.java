package me.kavin.piped.utils;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;

public class ExceptionHandler {

    public static Exception handle(Exception e) {
        return handle(e, null);
    }

    public static Exception handle(Exception e, String path) {

        if (e.getCause() != null && (e instanceof ExecutionException || e instanceof CompletionException))
            e = (Exception) e.getCause();

        if (!(e instanceof AgeRestrictedContentException || e instanceof ContentNotAvailableException
                || e instanceof GeographicRestrictionException)) {
            if (path != null)
                System.err.println("An error occoured in the path: " + path);
            e.printStackTrace();
        }

        return e;
    }
}
