package com.sparrowsoftware.grid;

import org.junit.Test;

import java.net.InetAddress;

public class TestRemoteRunner
{
    @Test
    public void test() throws Exception
    {
        RemoteTaskRunner runner = new RemoteTaskRunner();
        runner.serveTasks(8888, 50, InetAddress.getLoopbackAddress());
        runner.start();

    }
}
