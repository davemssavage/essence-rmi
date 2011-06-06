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

import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.freshvanilla.net.DataSocket;
import org.freshvanilla.net.DataSockets;
import org.freshvanilla.net.WireFormat;
import org.freshvanilla.utils.Callback;
import org.freshvanilla.utils.Classes;
import org.freshvanilla.utils.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmiInvocationHandler implements InvocationHandler, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(RmiInvocationHandler.class);

    private static final Object[] NO_OBJECTS = {};

    private final Factory<String, DataSocket> _factory;
    private final boolean _closeFactory;
    private final ConcurrentMap<Method, RmiMethod> _rmiMethodMap;

    public RmiInvocationHandler(Factory<String, DataSocket> factory, boolean closeFactory) {
        _factory = factory;
        _closeFactory = closeFactory;
        _rmiMethodMap = new ConcurrentHashMap<Method, RmiMethod>(31);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        }
        finally {
            super.finalize();
        }
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RmiMethod rmiMethod = getRmiMethod(method);
        boolean async = rmiMethod._async;

        if (args == null) {
            args = NO_OBJECTS;
        }

        int argsLength = args.length - (async ? 1 : 0);
        DataSocket ds = _factory.acquire(async ? "async-org.freshvanilla.rmi" : "sync-org.freshvanilla.rmi");

        try {
            final long sequenceNumber = (async ? ds.microTimestamp() : 0);

            if (async) {
                Callback<?> callback = (Callback<?>)args[argsLength];
                ds.addCallback(sequenceNumber, callback);
                // TODO silly: although this is needed only exactly once, it is done on
                // each call simply because the DataSocket creates its Executor for
                // reading async replies lazily.
                // Should probably configure a shared Executor on the DataSocketFactory
                // and have it inject that into all acquired() DataSockets; that would
                // bound the number of created Executors and avoid slamming into the
                // same synchronized block (checking for the Executor) on every call.
                // Then should have a simple boolean hasReader() on DataSocket so that
                // callers like this one can do it only once. Ideally the RmiCallback
                // would be created & set by the DataSocket itself on the first call to
                // addCallback().
                ds.setReader(new RmiCallback(ds));
            }

            WireFormat wf = ds.wireFormat();
            ByteBuffer wb = ds.writeBuffer();
            wf.writeNum(wb, sequenceNumber);
            wf.writeTag(wb, rmiMethod._methodName);
            wf.writeArray(wb, argsLength, args);
            wf.flush(ds, wb);

            if (async) {
                return null;
            }

            ByteBuffer rb = ds.read();
            long sequenceNumber2 = wf.readNum(rb);
            assert sequenceNumber2 == 0;
            boolean success = wf.readBoolean(rb);
            Object reply = wf.readObject(rb);

            if (success) {
                return Classes.parseAs(reply, rmiMethod._returnType);
            }

            if (reply instanceof Throwable) {
                Throwable t = (Throwable)reply;
                DataSockets.appendStackTrace(ds, t);
                throw t;
            }

            throw new AssertionError(reply);
        }
        finally {
            _factory.recycle(ds);
        }
    }

    public void close() {
        if (_closeFactory) {
            _factory.close();
        }
    }

    private RmiMethod getRmiMethod(Method method) {
        RmiMethod ret = _rmiMethodMap.get(method);
        if (ret == null) {
            ret = new RmiMethod(method.getName(), method.getReturnType(), method.getParameterTypes());
            RmiMethod prev = _rmiMethodMap.putIfAbsent(method, ret);
            if (prev != null) {
                ret = prev;
            }
        }
        return ret;
    }

    // Reader for replies to async calls on a given DataSocket
    static class RmiCallback implements Callback<DataSocket> {

        private final DataSocket ds;

        RmiCallback(DataSocket ds) {
            this.ds = ds;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void onCallback(DataSocket dataSocket) throws Exception {
            Callback callback = null;

            try {
                ByteBuffer rb = ds.read();
                final WireFormat wf = ds.wireFormat();
                long sequenceNumber = wf.readNum(rb);
                callback = ds.removeCallback(sequenceNumber);
                assert sequenceNumber != 0;
                boolean success = wf.readBoolean(rb);
                Object reply = wf.readObject(rb);

                if (callback == null) {
                    LOG.error("Response to unknown callback reply=" + reply);
                }
                else if (success) {
                    callback.onCallback(reply);
                }
                else {
                    callback.onException((Throwable)reply);
                }
            }
            catch (Exception e) {
                if (ds.isClosed()) {
                    return;
                }
                if (!(e instanceof AsynchronousCloseException)) {
                    LOG.error("Exception thrown processing callback", e);
                }
                try {
                    if (callback != null) {
                        callback.onException(e);
                    }
                }
                catch (Exception ignored) {
                    // ignored.
                }
            }
        }

        public void onException(Throwable t) {
            LOG.warn("Unhandled exception", t);
        }
    }

    // Wrapper for snapshotting Method name/parameters. Not necessary except for the fact
    // that Method.getParameterTypes() creates a new array on every call.
    static class RmiMethod {
        public final String _methodName;
        public final Class<?> _returnType;
        public final Class<?>[] _parameterTypes;
        public final boolean _async;

        RmiMethod(String methodName, Class<?> returnType, Class<?>[] parameterTypes) {
            _methodName = methodName;
            _returnType = returnType;
            _parameterTypes = parameterTypes;
            _async = (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1] == Callback.class);
        }
    }

}
