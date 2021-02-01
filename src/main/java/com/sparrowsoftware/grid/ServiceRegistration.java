package com.sparrowsoftware.grid;

import java.net.InetAddress;

public interface ServiceRegistration
{
    void joinCluster(InetAddress address, int port);
    void leaveCluster(InetAddress address, int port);
}
