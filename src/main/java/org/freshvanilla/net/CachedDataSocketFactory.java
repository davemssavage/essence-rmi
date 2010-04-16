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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.freshvanilla.lang.misc.Unsafe;
import org.freshvanilla.utils.Factory;
import org.freshvanilla.utils.VanillaResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedDataSocketFactory extends VanillaResource implements Factory<String, DataSocket> {

    private static final Logger LOG = LoggerFactory.getLogger(CachedDataSocketFactory.class);

    private final ConcurrentMap<String, DataSockets> dataSocketsMap = new ConcurrentHashMap<String, DataSockets>();
    private final Factory<String, DataSocket> dataSocketBuilder;
    private int maximumConnections = 4;

    public CachedDataSocketFactory(String name, String connectionString) {
        this(name, connectionString, Long.MAX_VALUE);
    }

    public CachedDataSocketFactory(String name, String connectionString, long timeoutMS) {
        this(name, new DataSocketFactory(name, connectionString, timeoutMS));
    }

    public CachedDataSocketFactory(String name, Factory<String, DataSocket> dataSocketBuilder) {
        super(name);
        this.dataSocketBuilder = dataSocketBuilder;
    }

    public int getMaximumConnections() {
        return maximumConnections;
    }

    public void setMaximumConnections(int maximumConnections) {
        this.maximumConnections = maximumConnections;
    }

    public DataSocket acquire(String description) throws InterruptedException {
        checkedClosed();
        DataSockets dataSockets = dataSocketsMap.get(description);
        if (dataSockets == null) {
            dataSocketsMap.putIfAbsent(description, new DataSockets(maximumConnections));
            dataSockets = dataSocketsMap.get(description);
        }
        DataSocket ds = acquire0(dataSockets, description);
        synchronized (dataSockets.used) {
            dataSockets.used.add(ds);
        }
        return ds;
    }

    private DataSocket acquire0(DataSockets dataSockets, String description) throws InterruptedException {
        // is there one free?
        DataSocket ds = dataSockets.free.poll();
        if (ds != null) {
            return ds;
        }

        // otherwise we might have to make one.
        if (!dataSockets.used.isEmpty()) {
            Thread.yield();
            // see if it was freed.
            ds = dataSockets.free.poll();
            if (ds != null) {
                return ds;
            }
        }

        // should not go over the maximum.
        int count = 1;
        while (dataSockets.used.size() >= maximumConnections) {
            Thread.sleep(1);
            // see if it was freed.
            ds = dataSockets.free.poll();
            if (ds != null) {
                if (count >= 1) {
                    LOG.debug(name + ": got a connection after " + count);
                }
                return ds;
            }
            count++;
        }

        // there is a race condition where this could appear less than the actual number.
        if (dataSockets.free.size() + dataSockets.used.size() >= maximumConnections) {
            return dataSockets.free.take();
        }

        try {
            return dataSocketBuilder.acquire(description);
        } catch (Exception e) {
            throw Unsafe.rethrow(e);
        }
    }

    public void recycle(DataSocket dataSocket) {
        if (dataSocket == null) {
            return;
        }

        DataSockets dataSockets = dataSocketsMap.get(dataSocket.getName());
        if (dataSockets == null) {
            LOG.warn(name + ": unexpected recycled object " + dataSocket);
            dataSocket.close();
            return;
        }

        synchronized (dataSockets.used) {
            dataSockets.used.remove(dataSocket);
        }

        if (isClosed()) {
            dataSocket.close();
        }
        else if (!dataSocket.isClosed()) {
            try {
                if (dataSockets.free.offer(dataSocket, 2, TimeUnit.MILLISECONDS)) {
                    dataSocket = null;
                }
                else {
                    LOG.debug(name + ": closing as over maximum connections " + dataSocket);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                if (dataSocket != null) {
                    dataSocket.close();
                }
            }
        }
    }

    public void close() {
        super.close();

        for (DataSockets dataSockets : dataSocketsMap.values()) {
            for (DataSocket socket : dataSockets.free) {
                socket.close();
            }

            synchronized (dataSockets.used) {
                for (DataSocket socket : dataSockets.used) {
                    socket.close();
                }
            }
        }

        dataSocketsMap.clear();
    }

    static class DataSockets {
        final BlockingQueue<DataSocket> free;
        final Set<DataSocket> used;

        DataSockets(int maximumConnections) {
            free = new ArrayBlockingQueue<DataSocket>(maximumConnections + 1);
            used = new HashSet<DataSocket>(maximumConnections);
        }
    }
}
