package com.sparrowsoftware.grid;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * First cut of remote execution.
 */
public class RemoteTaskRunner implements TaskRunner {

    private Queue<PromiseImpl<?>> tasks = new LinkedBlockingDeque<>();

    class PromiseImpl<T> implements Promise<T> {
        private Supplier<T> task;
        private final Object monitor = new Object();
        private volatile boolean finished = false;
        private volatile T result;

        PromiseImpl(Supplier<T> task) {
            this.task = task;
        }

        Supplier<T> getTask() {
            return task;
        }

        @Override
        public void completeResult(T result) {
            synchronized (monitor) {
                if (finished) {
                    throw new RuntimeException("Attempted to complete a promise twice, previous result="+result);
                }
                this.result = result;
                finished = true;
                monitor.notifyAll();
            }
        }

        @Override
        public <R> Promise<R> thenApply(Function<T, R> nextStep) {
            return execute( () -> {
                try {
                    synchronized (monitor) {
                        while (!finished) {
                            monitor.wait();
                        }
                    }
                    return nextStep.apply(result);
                }
                catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            });
        }

        @Override
        public <R> Promise<R> thenCompose(Function<T, Promise<R>> nextStep) {
            return execute( () -> {
                try {
                    synchronized (monitor) {
                        while (!finished) {
                            monitor.wait();
                        }
                    }
                    return nextStep.apply(result).fulfill();
                }
                catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            });
        }

        @Override
        public T fulfill() {
            synchronized (monitor) {
                while (!finished) {
                    try {
                        monitor.wait();
                    }
                    catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
            }
            return result;
        }

        @Override
        public T fulfill(long timeoutMillis, int nanos) throws InterruptedException {
            synchronized (monitor) {
                while (!finished) {
                    monitor.wait(timeoutMillis, nanos);
                }
            }
            return result;
        }
    }

    @Override
    public <T> Promise<T> execute(Supplier<T> task) {
        PromiseImpl promise = new PromiseImpl(task);
        tasks.add(promise);
        return promise;
    }
}
