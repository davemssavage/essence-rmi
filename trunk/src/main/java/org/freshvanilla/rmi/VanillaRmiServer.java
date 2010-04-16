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
package org.freshvanilla.rmi;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.freshvanilla.net.BinaryWireFormat;
import org.freshvanilla.net.DataSocket;
import org.freshvanilla.net.DataSocketFactory;
import org.freshvanilla.net.DataSocketHandler;
import org.freshvanilla.net.VanillaDataServerSocket;
import org.freshvanilla.net.WireFormat;
import org.freshvanilla.utils.Classes;
import org.freshvanilla.utils.Factory;
import org.freshvanilla.utils.VanillaResource;
import org.freshvanilla.utils.Classes.MetaMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanillaRmiServer<P> extends VanillaResource implements Factory<DataSocket, DataSocketHandler> {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaRmiServer.class);

    private final VanillaDataServerSocket serverSocket;
    private final P provider;
    private final AtomicInteger id = new AtomicInteger();
    private final MetaMethod<?>[] memberMethods;

    public VanillaRmiServer(String name, int port, P provider) throws IOException {
        super(name);
        this.provider = provider;
        memberMethods = Classes.getMemberMethods(provider.getClass());
        serverSocket = new VanillaDataServerSocket(name, this, new HashMap<String, Object>(), port,
            new BinaryWireFormat.Builder(name), DataSocketFactory.DEFAULT_MAXIMUM_MESSAGE_SIZE);
    }

    public int getPort() {
        return serverSocket.getPort();
    }

    public DataSocketHandler acquire(DataSocket dataSocket) throws InterruptedException {
        return new RmiDataSocketHandler(getName() + ':' + id.incrementAndGet(), dataSocket);
    }

    public void recycle(DataSocketHandler dataSocketHandler) {
        // TODO: why is this empty?
    }

    public P getProvider() {
        return provider;
    }

    public void close() {
        super.close();
        serverSocket.close();
    }

    public String getConnectionString() {
        return serverSocket.getConnectionString();
    }

    class RmiDataSocketHandler extends VanillaResource implements DataSocketHandler {
        private final DataSocket ds;
        private final WireFormat wf;
        private final Set<OnDisconnectionRunnable> onDisconnection =
                new LinkedHashSet<OnDisconnectionRunnable>();

        private RmiDataSocketHandler(String name, DataSocket ds) {
            super(name);
            this.ds = ds;
            wf = ds.wireFormat();
        }

        public void close() {
            if (!ds.isClosed()) {
                ds.close();
            }
            super.close();
        }

        public void onConnection() {
            // TODO: why is this empty?
        }

        @SuppressWarnings("unchecked")
        public void onMessage() throws IOException {
            long sequenceNumber = 0;
            boolean okay = false;
            Object result;

            try {
                final ByteBuffer rb = ds.read();
                sequenceNumber = wf.readNum(rb);
                String methodName = wf.readString(rb);
                final Object[] args = wf.readArray(rb);
                final MetaMethod<?> method = getMethodFor(methodName, args);
                final Class<?>[] types = method.parameterTypes;

                for (int i = 0; i < args.length; i++) {
                    args[i] = Classes.parseAs(args[i], types[i]);
                }

                if (method.getAnnotation(OnDisconnection.class) == null) {
                    result = ((MetaMethod)method).invoke(provider, args);
                } else {
                    onDisconnection.add(new OnDisconnectionRunnable(method, args));
                    result = null;
                }

                okay = true;
            } catch (InvocationTargetException e) {
                result = e.getCause();
            } catch (Exception e) {
                if (e.getClass() == IOException.class || e.getClass() == EOFException.class) {
                    LOG.debug(name + ": Dropping connection as client has disconnected " + e);
                    close();
                    return;
                }
                result = e;
            }

            final ByteBuffer wb = ds.writeBuffer();
            wf.writeNum(wb, sequenceNumber);
            wf.writeBoolean(wb, okay);
            wf.writeObject(wb, result);

            try {
                wf.flush(ds, wb);
            } catch (IOException e) {
                close();
                if (result instanceof IOException) {
                    return;
                }
                LOG.warn(name + ": Unable to send result=" + result + " as client has disconnected " + e);
            }
        }

        private MetaMethod<?> getMethodFor(String methodName, Object[] args) {
            for (MetaMethod<?> method : memberMethods) {
                if (methodName.equals(method.methodName) && method.parameterTypes.length == args.length) {
                    return method;
                }
            }
            throw new UnsupportedOperationException("Unable to find method " + methodName + " for " + provider.getClass() + " with " + args.length + " arguments.");
        }

        public void onDisconnection() {
            for (Runnable runnable : onDisconnection) {
                runnable.run();
            }
            close();
        }
    }

    class OnDisconnectionRunnable implements Runnable {
        private final MetaMethod<?> method;
        private final Object[] args;

        OnDisconnectionRunnable(MetaMethod<?> method, Object[] args) {
            this.method = method;
            this.args = args;
        }

        @SuppressWarnings("unchecked")
        public void run() {
            try {
                ((MetaMethod)method).invoke(provider, args);
            } catch (InvocationTargetException e) {
                LOG.warn(name + ": Exception thrown on disconnect.", e.getCause());
            }
        }

        public int hashCode() {
            return method.hashCode() ^ System.identityHashCode(provider) ^ Arrays.hashCode(args);
        }

        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != getClass()) return false;
            OnDisconnectionRunnable odr = (OnDisconnectionRunnable) obj;
            return method.method.equals(odr.method.method) && Arrays.equals(args, odr.args);
        }
    }

}
