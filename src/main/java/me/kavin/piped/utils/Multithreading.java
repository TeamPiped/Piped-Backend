package me.kavin.piped.utils;

import java.util.concurrent.*;
import java.util.function.Supplier;

public class Multithreading {

    private static final ExecutorService es = Executors.newVirtualThreadPerTaskExecutor();
    private static final ExecutorService esLimited = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 8);
    private static final ExecutorService esLimitedPubSub = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private static final ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

    public static void runAsync(final Runnable runnable) {
        es.submit(runnable);
    }

    public static void runAsyncTask(final ForkJoinTask<?> task) {
        forkJoinPool.submit(task);
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

    public static <U> Future<U> supplyAsync(Supplier<U> supplier) {
        return es.submit(supplier::get);
    }
}
