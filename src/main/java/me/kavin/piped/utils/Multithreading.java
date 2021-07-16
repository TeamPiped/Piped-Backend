package me.kavin.piped.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Multithreading {

    private static final ExecutorService es = Executors.newCachedThreadPool();
    private static final ExecutorService esLimited = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private static final ExecutorService esLimitedPubSub = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void runAsync(final Runnable runnable) {
        es.submit(runnable);
    }

    public static void runAsyncLimited(final Runnable runnable) {
        esLimited.submit(runnable);
    }

    public static void runAsyncLimitedPubSub(final Runnable runnable) {
        esLimited.submit(runnable);
    }

    public static ExecutorService getCachedExecutor() {
        return es;
    }
}