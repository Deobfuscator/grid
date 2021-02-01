package com.sparrowsoftware.grid;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

class PromiseImpl<T> implements Promise<T>
{
    private static AtomicInteger counter = new AtomicInteger(0);
    private TaskRunner runner;
    private Supplier<T> task;
    private int id;
    private final Object monitor = new Object();
    private volatile boolean finished = false;
    private volatile T result;

    PromiseImpl(TaskRunner runner, Supplier<T> task)
    {
        this.runner = runner;
        this.task = task;
        this.id = counter.incrementAndGet();
    }

    @Override
    public int getId()
    {
        return id;
    }

    @Override
    public Supplier<T> getTask()
    {
        return task;
    }

    @Override
    public void completeResult(T result)
    {
        synchronized (monitor)
        {
            if (finished)
            {
                throw new RuntimeException("Attempted to complete a promise twice, previous result=" + result);
            }
            this.result = result;
            finished = true;
            monitor.notifyAll();
        }
    }

    @Override
    public <R> Promise<R> thenApply(Function<T, R> nextStep)
    {
        return runner.execute(() ->
        {
            try
            {
                synchronized (monitor)
                {
                    while (!finished)
                    {
                        monitor.wait();
                    }
                }
                return nextStep.apply(result);
            } catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        });
    }

    @Override
    public <R> Promise<R> thenCompose(Function<T, Promise<R>> nextStep)
    {
        return runner.execute(() ->
        {
            try
            {
                synchronized (monitor)
                {
                    while (!finished)
                    {
                        monitor.wait();
                    }
                }
                return nextStep.apply(result).fulfill();
            } catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        });
    }

    @Override
    public T fulfill()
    {
        synchronized (monitor)
        {
            while (!finished)
            {
                try
                {
                    monitor.wait();
                } catch (Throwable t)
                {
                    throw new RuntimeException(t);
                }
            }
        }
        return result;
    }

    @Override
    public T fulfill(long timeoutMillis, int nanos) throws InterruptedException
    {
        synchronized (monitor)
        {
            while (!finished)
            {
                monitor.wait(timeoutMillis, nanos);
            }
        }
        return result;
    }
}