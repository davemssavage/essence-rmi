/*
 Copyright 2008-2011 the original author or authors

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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.freshvanilla.lang.MetaClasses;
import org.freshvanilla.net.BinaryWireFormat;
import org.freshvanilla.net.DataSocket;
import org.freshvanilla.net.DataSocketFactory;
import org.freshvanilla.net.DataSocketHandler;
import org.freshvanilla.net.VanillaDataServerSocket;
import org.freshvanilla.net.WireFormat;
import org.freshvanilla.utils.Classes;
import org.freshvanilla.utils.Classes.MetaMethod;
import org.freshvanilla.utils.Factory;
import org.freshvanilla.utils.VanillaResource;

public class VanillaRmiServer<P> extends VanillaResource implements Factory<DataSocket, DataSocketHandler> {

    private final VanillaDataServerSocket _serverSocket;
    private final P _provider;
    private final AtomicInteger _id = new AtomicInteger();
    private final List<MetaMethod<?>> _memberMethods;

    public VanillaRmiServer(String name, int port, P provider) throws IOException {
        this(name, port, provider, Classes.getClassLoader(provider.getClass()));
    }

    public VanillaRmiServer(String name, int port, P provider, ClassLoader classLoader) throws IOException {
        super(name);
        _provider = provider;
        _memberMethods = Classes.getMemberMethods(provider.getClass());
        _serverSocket = new VanillaDataServerSocket(name, this, new HashMap<String, Object>(), port,
            new BinaryWireFormat.Builder(name, new MetaClasses(classLoader)),
            DataSocketFactory.DEFAULT_MAXIMUM_MESSAGE_SIZE);
    }

    public int getPort() {
        return _serverSocket.getPort();
    }

    public DataSocketHandler acquire(DataSocket dataSocket) throws InterruptedException {
        return new RmiDataSocketHandler(getName() + ':' + _id.incrementAndGet(), dataSocket);
    }

    public void recycle(DataSocketHandler dataSocketHandler) {
        // nothing to recycle
    }

    public P getProvider() {
        return _provider;
    }

    public void close() {
        super.close();
        _serverSocket.close();
    }

    public String getConnectionString() {
        return _serverSocket.getConnectionString();
    }

    class RmiDataSocketHandler extends VanillaResource implements DataSocketHandler {
        private final DataSocket _ds;
        private final WireFormat _wf;
        private final Set<OnDisconnectionRunnable> _onDisconnection = new LinkedHashSet<OnDisconnectionRunnable>();

        private RmiDataSocketHandler(String name, DataSocket ds) {
            super(name);
            _ds = ds;
            _wf = ds.wireFormat();
        }

        public void close() {
            try {
                if (!_ds.isClosed()) {
                    _ds.close();
                }
            }
            finally {
                super.close();
            }
        }

        public void onConnection() {
            // TODO: why is this empty?
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void onMessage() throws IOException {
            long sequenceNumber = 0;
            boolean okay = false;
            Object result;

            try {
                final ByteBuffer rb = _ds.read();
                sequenceNumber = _wf.readNum(rb);
                String methodName = _wf.readString(rb);
                final Object[] args = _wf.readArray(rb);
                final MetaMethod<?> method = getMethodFor(methodName, args);
                final Class<?>[] types = method.parameterTypes;

                for (int i = 0; i < args.length; i++) {
                    args[i] = Classes.parseAs(args[i], types[i]);
                }

                if (method.getAnnotation(OnDisconnection.class) == null) {
                    result = ((MetaMethod)method).invoke(_provider, args);
                }
                else {
                    _onDisconnection.add(new OnDisconnectionRunnable(method, args));
                    result = null;
                }

                okay = true;
            }
            catch (InvocationTargetException e) {
                result = e.getCause();
            }
            catch (Exception e) {
                if (e.getClass() == IOException.class || e.getClass() == EOFException.class) {
                    getLog().debug(getName() + ": Dropping connection as client has disconnected " + e);
                    close();
                    return;
                }
                result = e;
            }

            final ByteBuffer wb = _ds.writeBuffer();
            _wf.writeNum(wb, sequenceNumber);
            _wf.writeBoolean(wb, okay);
            _wf.writeObject(wb, result);

            try {
                _wf.flush(_ds, wb);
            }
            catch (IOException e) {
                close();
                if (result instanceof IOException) {
                    return;
                }
                getLog().warn(
                    getName() + ": Unable to send result=" + result + " as client has disconnected " + e);
            }
        }

        private MetaMethod<?> getMethodFor(String methodName, Object[] args) {
            for (MetaMethod<?> method : _memberMethods) {
                if (methodName.equals(method.methodName) && method.parameterTypes.length == args.length) {
                    return method;
                }
            }
            throw new UnsupportedOperationException("Unable to find method " + methodName + " for "
                                                    + _provider.getClass() + " with " + args.length
                                                    + " arguments.");
        }

        public void onDisconnection() {
            for (Runnable runnable : _onDisconnection) {
                runnable.run();
            }
            close();
        }
    }

    class OnDisconnectionRunnable implements Runnable {
        private final MetaMethod<?> _method;
        private final Object[] _args;

        OnDisconnectionRunnable(MetaMethod<?> method, Object[] args) {
            _method = method;
            _args = args;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void run() {
            try {
                ((MetaMethod)_method).invoke(_provider, _args);
            }
            catch (InvocationTargetException e) {
                getLog().warn(getName() + ": Exception thrown on disconnect.", e.getCause());
            }
        }

        public int hashCode() {
            return _method.hashCode() ^ System.identityHashCode(_provider) ^ Arrays.hashCode(_args);
        }

        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }

            @SuppressWarnings("unchecked")
            OnDisconnectionRunnable odr = (OnDisconnectionRunnable)obj;
            return _method.method.equals(odr._method.method) && Arrays.equals(_args, odr._args);
        }
    }

}
