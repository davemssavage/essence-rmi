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
package org.freshvanilla.rmi.internal;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.freshvanilla.net.DataSocket;
import org.freshvanilla.net.WireFormat;
import org.freshvanilla.utils.Callback;
import org.freshvanilla.utils.Classes;
import org.freshvanilla.utils.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmiInvocationHandler implements InvocationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RmiInvocationHandler.class);

    private static final Object[] NO_OBJECTS = {};

    private final Factory<String, DataSocket> factory;
    private final boolean closeFactory;

    public RmiInvocationHandler(Factory<String, DataSocket> factory, boolean closeFactory) {
        this.factory = factory;
        this.closeFactory = closeFactory;
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
        if (args == null || args.length == 0) {
            final String methodName = method.getName();
            if ("isClosed".equals(methodName)) {
                return factory.isClosed();
            } else if ("getName".equals(methodName)) {
                return factory.getName();
            } else if ("close".equals(methodName)) {
                close();
                return null;
            }
        }

        RmiInvocationHandler.RmiMethod rmiMethod = getRmiMethod(method);
        final Class<?>[] parameterTypes = rmiMethod.parameterTypes;
        boolean async = parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1] == Callback.class;

        if (args == null) {
            args = NO_OBJECTS;
        }

        int argsLength = args.length - (async ? 1 : 0);
        final DataSocket ds = factory.acquire(async ? "async-org.freshvanilla.rmi" : "sync-org.freshvanilla.rmi");

        try {
            // write the sequence number.
            final long sequenceNumber = async ? ds.microTimestamp() : 0;
            if (async) {
                Callback<?> callback = (Callback<?>)args[args.length - 1];
                ds.addCallback(sequenceNumber, callback);
                ds.setReader(new RmiCallback(ds));
            }

            final WireFormat wf = ds.wireFormat();
            ByteBuffer wb = ds.writeBuffer();
            wf.writeNum(wb, sequenceNumber);
            wf.writeTag(wb, rmiMethod.methodName);
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
                return Classes.parseAs(reply, rmiMethod.returnType);
            }

            if (reply instanceof Throwable) {
                Throwable t = (Throwable) reply;
                appendStackTrace(ds, t);
                throw t;
            }

            throw new AssertionError(reply);
        } finally {
            factory.recycle(ds);
        }
    }

    private static void appendStackTrace(DataSocket ds, Throwable t) {
        try {
            Field stackTrace = Throwable.class.getDeclaredField("stackTrace");
            stackTrace.setAccessible(true);
            final List<StackTraceElement> stack1 = Arrays.asList(t.getStackTrace());
            int pos;

            for (pos = stack1.size() - 1; pos > 0; pos--) {
                if ("invoke0".equals(stack1.get(pos).getMethodName())) {
                    break;
                }
            }

            if (pos <= 0) {
                pos = stack1.size() - 6;
            }

            List<StackTraceElement> stack = new ArrayList<StackTraceElement>(stack1.subList(0, pos));
            final InetSocketAddress address = ds.getAddress();

            if (address != null) {
                String hostName = address.getHostName();
                if ("0.0.0.0".equals(hostName)) {
                    hostName = "localhost";
                }
                stack.add(new StackTraceElement("~ call to server ~", "call", hostName, address.getPort()));
            }

            Throwable t2 = new Throwable();
            final List<StackTraceElement> stack2 = Arrays.asList(t2.getStackTrace());
            stack.addAll(stack2.subList(3, stack2.size()));
            stackTrace.set(t, stack.toArray(new StackTraceElement[stack.size()]));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void close() {
        if (closeFactory) {
            factory.close();
        }
    }

    private final Map<Method, RmiInvocationHandler.RmiMethod> rmiMethodMap = new ConcurrentHashMap<Method, RmiInvocationHandler.RmiMethod>();

    private RmiInvocationHandler.RmiMethod getRmiMethod(Method method) {
        RmiInvocationHandler.RmiMethod ret = rmiMethodMap.get(method);
        if (ret == null) {
            rmiMethodMap.put(method, ret = new RmiInvocationHandler.RmiMethod(method.getName(), method.getReturnType(), method.getParameterTypes()));
        }
        return ret;
    }

    static class RmiCallback implements Callback<DataSocket> {

        private final DataSocket ds;

        RmiCallback(DataSocket ds) {
            this.ds = ds;
        }

        @SuppressWarnings("unchecked")
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
                    callback.onException((Throwable) reply);
                }
            } catch (Exception e) {
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
                } catch (Exception ignored) {
                    // ignored.
                }
            }
        }

        public void onException(Throwable t) {
            LOG.warn("Unhandled exception", t);
        }
    }

    static class RmiMethod {
        public final String methodName;
        public final Class<?> returnType;
        public final Class<?>[] parameterTypes;
    
        RmiMethod(String methodName, Class<?> returnType, Class<?>[] parameterTypes) {
            this.methodName = methodName;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }
    }

}