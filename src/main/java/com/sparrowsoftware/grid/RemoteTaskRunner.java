package com.sparrowsoftware.grid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * First cut of remote execution.
 */
public class RemoteTaskRunner implements TaskRunner {
    private static final Logger log = LogManager.getLogger(RemoteTaskRunner.class);

    private ServerSocket socket;
    private List<Thread> clientThreads = new ArrayList<>();
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

    class ClientThread extends Thread {
        private Socket socket;
        private Throwable err;

        public ClientThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();
            } catch (Throwable err) {
                this.err = err;
            }
        }
    }

    @Override
    public <T> Promise<T> execute(Supplier<T> task) {
        PromiseImpl promise = new PromiseImpl(task);
        tasks.add(promise);
        return promise;
    }

    public RemoteTaskRunner(int port, int backlog, InetAddress address) throws IOException {
        socket = new ServerSocket(port, backlog, address);
        log.debug("Running RemoteTaskRunner. Port={}, backlog={}, address={}", port, backlog, address);
    }

    public void run() throws IOException {
        while(true) {
            Socket clientSocket = socket.accept();
            ClientThread clientThread = new ClientThread(clientSocket);
            clientThreads.add(clientThread);
            clientThread.start();
        }
    }
}
