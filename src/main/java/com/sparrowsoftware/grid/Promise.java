package com.sparrowsoftware.grid;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is a much more narrow alternative to CompletableFuture, without
 * implementation getting baked in
 *
 * @param <T>
 */
public interface Promise<T>
{
    static <T> Promise<T> of(
            TaskRunner runner,
            Supplier<T> task
    )
    {
        return new PromiseImpl<>(runner, task);
    }

    int getId();

    Supplier<T> getTask();

    void completeResult(T result);

    <R> Promise<R> thenApply(Function<T, R> nextStep);

    <R> Promise<R> thenCompose(Function<T, Promise<R>> nextStep);

    T fulfill();

    T fulfill(long timeoutMillis, int nanos) throws InterruptedException;
}
