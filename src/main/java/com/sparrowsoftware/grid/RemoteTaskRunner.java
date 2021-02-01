package com.sparrowsoftware.grid;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;

/**
 * First cut of remote execution.
 */
public class RemoteTaskRunner extends Thread implements TaskRunner
{
    private static final Logger log = LogManager.getLogger(RemoteTaskRunner.class);

    private ServerSocket socket;
    private final List<Thread> clientThreads = new ArrayList<>();
    private final Queue<Promise<?>> pendingPromises = new LinkedBlockingDeque<>();
    private final ConcurrentHashMap<Integer, Promise<?>> activePromises = new ConcurrentHashMap<>();

    public enum Command
    {
        GET_TASK,
        POST_RESULT
    }

    class ClientThread extends Thread
    {
        private final Socket socket;
        private final Input in;
        private final Output out;
        private Throwable err;
        private final Kryo kryo;

        public ClientThread(Socket socket) throws IOException
        {
            this.socket = socket;
            this.in = new Input(socket.getInputStream());
            this.out = new Output(socket.getOutputStream());
            this.kryo = new Kryo();
            this.kryo.setRegistrationRequired(false);
        }

        public void run()
        {
            try
            {
                Command command = kryo.readObject(in, Command.class);
                switch (command)
                {
                    case GET_TASK -> getTask();
                    case POST_RESULT -> postResult();
                }

            } catch (Throwable err)
            {
                this.err = err;
            }
        }

        private void getTask()
        {
            Promise<?> promise = pendingPromises.remove();
            activePromises.put(promise.getId(), promise);
            kryo.writeClassAndObject(out, promise.getTask());
        }

        private void postResult()
        {
            int id = kryo.readObject(in, Integer.class);
            Object result = kryo.readClassAndObject(in);
            Promise<?> promise = activePromises.remove(id);
            ((Promise) promise).completeResult(result);
        }
    }

    public RemoteTaskRunner(

    )
    {

    }

    @Override
    public <T> Promise<T> execute(Supplier<T> task)
    {
        Promise<T> promise = Promise.of(this, task);
        pendingPromises.add(promise);
        return promise;
    }

    public void serveTasks(int port, int backlog, InetAddress address) throws IOException
    {
        socket = new ServerSocket(port, backlog, address);
        log.debug("RemoteTaskRunner is serving tasks. Port={}, backlog={}, address={}", port, backlog, address);
        start();
    }

    // registration should be handled as remote directory
    // with each entry being a subdirectory with host name
    // inside we have 1 file called settings and another called stats

    @Override
    public void run()
    {
        try
        {
            while (true)
            {
                Socket clientSocket = socket.accept();
                log.info("Got connection from {}", clientSocket.getInetAddress());
                ClientThread clientThread = new ClientThread(clientSocket);
                clientThreads.add(clientThread);
                clientThread.start();
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
