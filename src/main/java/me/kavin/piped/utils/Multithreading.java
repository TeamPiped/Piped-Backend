package me.kavin.piped.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class Multithreading {

    private static final ExecutorService es = Executors.newCachedThreadPool();
    private static final ExecutorService esLimited = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 8);
    private static final ExecutorService esLimitedPubSub = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void runAsync(final Runnable runnable) {
        es.submit(runnable);
    }

    public static void runAsyncLimited(final Runnable runnable) {
        esLimited.submit(runnable);
    }

    public static void runAsyncLimitedPubSub(final Runnable runnable) {
        esLimitedPubSub.submit(runnable);
    }

    public static ExecutorService getCachedExecutor() {
        return es;
    }

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return CompletableFuture.supplyAsync(supplier, es);
    }
}
