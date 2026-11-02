package me.micahcode.hqtiers.client.fetching;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HqTiersRequestQueue implements AutoCloseable {
    private static final int MAX_PENDING = 256;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("hqtiers-dispatch").factory());
    private final ExecutorService workers;
    private final List<Pending<?>> pending = new ArrayList<>();
    private final int maxConcurrent;
    private ScheduledFuture<?> dispatchTask;
    private int running;
    private long pausedUntil;
    private boolean closed;

    public HqTiersRequestQueue() {
        this(16);
    }

    HqTiersRequestQueue(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
        this.workers = Executors.newFixedThreadPool(maxConcurrent,
                Thread.ofPlatform().daemon().name("hqtiers-fetch-", 0).factory());
    }

    synchronized <T> CompletableFuture<T> submit(Callable<T> task, long delayMillis) {
        if (closed || pending.size() >= MAX_PENDING) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Tier request queue is full or closed"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(new Pending<>(task, result, deadline(delayMillis)));
        scheduleDispatch();
        return result;
    }

    private synchronized void scheduleDispatch() {
        if (dispatchTask != null) dispatchTask.cancel(false);
        dispatchTask = null;
        if (closed || pending.isEmpty() || running >= maxConcurrent) return;
        long readyAt = pending.stream().mapToLong(Pending::readyAt).min().orElse(0);
        long startAt = Math.max(readyAt, pausedUntil);
        dispatchTask = scheduler.schedule(this::dispatch, Math.max(0, startAt - now()), TimeUnit.MILLISECONDS);
    }

    private synchronized void dispatch() {
        dispatchTask = null;
        if (closed || pending.isEmpty() || running >= maxConcurrent) return;
        long now = now();
        if (now < pausedUntil) {
            scheduleDispatch();
            return;
        }
        Pending<?> selected = null;
        for (Pending<?> candidate : pending) {
            if (candidate.readyAt() > now) continue;
            selected = candidate;
            break;
        }
        if (selected == null) {
            scheduleDispatch();
            return;
        }
        pending.remove(selected);
        running++;
        Pending<?> request = selected;
        workers.execute(() -> execute(request));
        scheduleDispatch();
    }

    private <T> void execute(Pending<T> request) {
        T value = null;
        Exception failure = null;
        try {
            value = request.task().call();
        } catch (Exception exception) {
            failure = exception;
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        synchronized (this) {
            running--;
            if (failure instanceof HqTiersHttpException http && http.retryAfterMillis() > 0) {
                pausedUntil = Math.max(pausedUntil, deadline(http.retryAfterMillis()));
            }
            scheduleDispatch();
        }
        if (failure == null) request.result().complete(value);
        else request.result().completeExceptionally(failure);
    }

    private static long now() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private static long deadline(long delay) {
        long now = now();
        return now + Math.min(Math.max(0, delay), Long.MAX_VALUE - now);
    }

    @Override
    public void close() {
        List<Pending<?>> abandoned;
        synchronized (this) {
            closed = true;
            abandoned = List.copyOf(pending);
            pending.clear();
            if (dispatchTask != null) dispatchTask.cancel(false);
        }
        scheduler.shutdownNow();
        workers.shutdownNow();
        abandoned.forEach(request -> request.result().completeExceptionally(
                new RejectedExecutionException("Tier request queue is closed")));
    }

    private record Pending<T>(Callable<T> task, CompletableFuture<T> result, long readyAt) {}
}
