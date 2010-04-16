/*
 Copyright 2008 Peter Lawrey

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.freshvanilla.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.freshvanilla.lang.ObjectBuilder;
import org.freshvanilla.utils.Factory;
import org.freshvanilla.utils.VanillaResource;

public class DataSocketFactory extends VanillaResource implements Factory<String, DataSocket> {
    
    public static final int DEFAULT_MAXIMUM_MESSAGE_SIZE = 1024 * 1024;

    private final InetSocketAddress[] addresses;
    private int lastAddress = 0;
    private ObjectBuilder<WireFormat> wireFormatBuilder = null;
    private final Map<String, Object> header = new LinkedHashMap<String, Object>();
    private int maximumMessageSize = DEFAULT_MAXIMUM_MESSAGE_SIZE;
    private final long timeoutMS;

    public DataSocketFactory(String name, String connectionString, long timeoutMS) {
        super(name);
        addresses = parseConnectionString(connectionString);
        this.timeoutMS = timeoutMS;
    }

    public ObjectBuilder<WireFormat> getWireFormatBuilder() {
        return wireFormatBuilder;
    }

    public void setWireFormatBuilder(ObjectBuilder<WireFormat> wireFormatBuilder) {
        this.wireFormatBuilder = wireFormatBuilder;
    }

    public Map<String, Object> getHeader() {
        return header;
    }

    public int getMaximumMessageSize() {
        return maximumMessageSize;
    }

    public void setMaximumMessageSize(int maximumMessageSize) {
        this.maximumMessageSize = maximumMessageSize;
    }

    private static InetSocketAddress[] parseConnectionString(String connectionString) {
        String[] parts = connectionString.split(",");
        InetSocketAddress[] addresses = new InetSocketAddress[parts.length];

        for (int i = 0; i < parts.length; i++) {
            String[] hostnamePort = parts[i].split(":");
            if (hostnamePort.length == 1) {
                int port = Integer.parseInt(hostnamePort[0]);
                addresses[i] = new InetSocketAddress(port);
            } else {
                String hostname = hostnamePort[0];
                int port = Integer.parseInt(hostnamePort[1]);
                if (hostname.length() == 0 || "localhost".equals(hostname)) {
                    addresses[i] = new InetSocketAddress(port);
                } else {
                    addresses[i] = new InetSocketAddress(hostname, port);
                }
            }
        }

        return addresses;
    }

    public DataSocketFactory(String name, InetSocketAddress[] addresses, long timeoutMS) {
        super(name);
        this.addresses = addresses;
        this.timeoutMS = timeoutMS;
    }

    public DataSocket acquire(String name) throws Exception {
        int count = 1;
        WireFormat wireFormat = wireFormatBuilder == null ? new BinaryWireFormat() : wireFormatBuilder.create();
        Map<String, Object> header = new LinkedHashMap<String, Object>(this.header);
        long timeoutMS = this.timeoutMS < Long.MAX_VALUE ? System.currentTimeMillis() + this.timeoutMS : Long.MAX_VALUE;
        IOException lastException;

        do {
            try {
                final InetSocketAddress remote = addresses[lastAddress];
                SocketChannel channel = SocketChannel.open(remote);
                return new VanillaDataSocket(name, remote, channel, wireFormat, header, maximumMessageSize);
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }

                if (lastAddress + 1 >= addresses.length) {
                    lastAddress = 0;
                }
                else {
                    lastAddress++;
                }

                lastException = e;
            }

            if (count == addresses.length) {
                getLog().debug(name + ": unable to connect to any of " + Arrays.asList(addresses));
                Thread.sleep(2500);
                count = 0;
            } else {
                count++;
            }
        } while (System.currentTimeMillis() < timeoutMS);

        throw lastException;
    }

    public void recycle(DataSocket dataSocket) {
        dataSocket.close();
    }

    protected void finalize() throws Throwable {
        try {
            close();
        }
        finally {
            super.finalize();
        }
    }

}
