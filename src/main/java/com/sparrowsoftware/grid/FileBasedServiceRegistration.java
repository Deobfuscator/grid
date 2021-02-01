package com.sparrowsoftware.grid;

import java.net.InetAddress;
import java.nio.file.Path;

public class FileBasedServiceRegistration
    implements
        ServiceRegistration
{
    private final Path regPath;

    public FileBasedServiceRegistration(Path regPath)
    {
        this.regPath = regPath;
    }

    private Path toPath(InetAddress address, int port)
    {
        return regPath.resolve(address.toString().replace(':', '_')+"-"+port);
    }

    @Override
    public void joinCluster(InetAddress address, int port)
    {
        toPath(address, port).toFile().mkdirs();
    }

    @Override
    public void leaveCluster(InetAddress address, int port)
    {
        toPath(address, port).toFile().delete();
    }
}
