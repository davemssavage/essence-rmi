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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.freshvanilla.utils.NamedThreadFactory;

public class DataSockets {
    private static final AtomicReference<ScheduledExecutorService> MANAGER = new AtomicReference<ScheduledExecutorService>();
    private static final Map<DataSocket, String> DATA_SOCKETS = new ConcurrentHashMap<DataSocket, String>();
    static final long CHECK_PERIOD_MS = 100;

    private DataSockets() {
        // forbidden
    }

    public static void registerDataSocket(DataSocket ds) {
        synchronized (MANAGER) {
            ScheduledExecutorService service = MANAGER.get();
            if (service == null || service.isShutdown()) {
                MANAGER.set(service = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(
                    "data-socket-manager", Thread.NORM_PRIORITY, true)));
                service.scheduleAtFixedRate(new DataSocketsChecker(), CHECK_PERIOD_MS, CHECK_PERIOD_MS,
                    TimeUnit.MILLISECONDS);
            }
        }
        DATA_SOCKETS.put(ds, "");
    }

    public static void unregisterDataSocket(DataSocket ds) {
        DATA_SOCKETS.remove(ds);
        synchronized (MANAGER) {
            if (DATA_SOCKETS.isEmpty()) reset();
        }
    }

    public static void reset() {
        ScheduledExecutorService service = MANAGER.getAndSet(null);
        if (service != null) service.shutdownNow();
        for (DataSocket dataSocket : DATA_SOCKETS.keySet())
            dataSocket.close();
        DATA_SOCKETS.clear();
    }

    private static class DataSocketsChecker implements Runnable {
        public void run() {
            long now = System.currentTimeMillis();
            for (DataSocket dataSocket : DATA_SOCKETS.keySet()) {
                dataSocket.timedCheck(now);
                if (dataSocket.isClosed()) DATA_SOCKETS.remove(dataSocket);
            }
        }
    }
}
