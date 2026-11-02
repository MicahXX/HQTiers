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
import java.util.function.IntSupplier;

public class HqTiersRequestQueue implements AutoCloseable {
    static final int BACKGROUND = 0;
    static final int FIRST_LOAD = 1;
    static final int NEARBY = 2;
    private static final int MAX_CONCURRENT = 8;
    private static final int MAX_NON_NEARBY = 6;
    private static final int BURST_SIZE = 8;
    private static final int MAX_PENDING = 256;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("hqtiers-dispatch").factory());
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_CONCURRENT,
            Thread.ofPlatform().daemon().name("hqtiers-fetch-", 0).factory());
    private final List<Pending<?>> pending = new ArrayList<>();
    private final long intervalMillis;
    private ScheduledFuture<?> dispatchTask;
    private int running;
    private int runningNonNearby;
    private int consecutiveNearby;
    private int tokens = BURST_SIZE;
    private long refilledAt = now();
    private long pausedUntil;
    private boolean closed;

    public HqTiersRequestQueue() {
        this(125);
    }

    HqTiersRequestQueue(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    synchronized <T> CompletableFuture<T> submit(Callable<T> task, IntSupplier priority, long delayMillis) {
        if (closed || pending.size() >= MAX_PENDING) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Tier request queue is full or closed"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(new Pending<>(task, result, priority, deadline(delayMillis)));
        scheduleDispatch();
        return result;
    }

    synchronized void prioritiesChanged() {
        scheduleDispatch();
    }

    private synchronized void scheduleDispatch() {
        if (dispatchTask != null) dispatchTask.cancel(false);
        dispatchTask = null;
        if (closed || pending.isEmpty() || running >= MAX_CONCURRENT) return;
        long readyAt = pending.stream().filter(this::hasCapacity).mapToLong(Pending::readyAt).min().orElse(-1);
        if (readyAt == -1) return;
        long now = now();
        refill(now);
        long tokenReadyAt = tokens > 0 ? now : refilledAt + intervalMillis;
        long startAt = Math.max(readyAt, Math.max(tokenReadyAt, pausedUntil));
        dispatchTask = scheduler.schedule(this::dispatch, Math.max(0, startAt - now), TimeUnit.MILLISECONDS);
    }

    private synchronized void dispatch() {
        dispatchTask = null;
        if (closed || pending.isEmpty() || running >= MAX_CONCURRENT) return;
        long now = now();
        refill(now);
        if (tokens == 0 || now < pausedUntil) {
            scheduleDispatch();
            return;
        }
        Pending<?> selected = null;
        int selectedPriority = Integer.MIN_VALUE;
        boolean nearby = false;
        for (Pending<?> candidate : pending) {
            if (candidate.readyAt() > now) continue;
            int priority = candidate.priority().getAsInt();
            boolean candidateNearby = priority == NEARBY;
            if (!candidateNearby && runningNonNearby >= MAX_NON_NEARBY) continue;
            if (consecutiveNearby >= 3 && priority == NEARBY) priority = -1;
            if (selected == null || priority > selectedPriority) {
                selected = candidate;
                selectedPriority = priority;
                nearby = candidateNearby;
            }
        }
        if (selected == null) {
            scheduleDispatch();
            return;
        }
        pending.remove(selected);
        if (nearby) consecutiveNearby++;
        else {
            runningNonNearby++;
            consecutiveNearby = 0;
        }
        running++;
        tokens--;
        Pending<?> request = selected;
        boolean selectedNearby = nearby;
        workers.execute(() -> execute(request, selectedNearby));
        scheduleDispatch();
    }

    private boolean hasCapacity(Pending<?> request) {
        return request.priority().getAsInt() == NEARBY || runningNonNearby < MAX_NON_NEARBY;
    }

    private void refill(long now) {
        if (now <= refilledAt) return;
        long added = (now - refilledAt) / intervalMillis;
        tokens = (int) Math.min(BURST_SIZE, tokens + added);
        refilledAt += added * intervalMillis;
    }

    private <T> void execute(Pending<T> request, boolean nearby) {
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
            if (!nearby) runningNonNearby--;
            if (failure instanceof HqTiersHttpException http && http.retryAfterMillis() > 0) {
                pausedUntil = Math.max(pausedUntil, deadline(http.retryAfterMillis()));
                tokens = 0;
                refilledAt = pausedUntil;
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

    private record Pending<T>(Callable<T> task, CompletableFuture<T> result, IntSupplier priority, long readyAt) {}
}
