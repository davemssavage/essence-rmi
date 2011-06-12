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

package org.freshvanilla.net;

import java.lang.reflect.Field;

import org.freshvanilla.lang.MetaClasses;
import org.freshvanilla.rmi.Proxies;
import org.freshvanilla.rmi.VanillaRmiServer;
import org.freshvanilla.test.AbstractTestCase;
import org.freshvanilla.utils.Classes;
import org.junit.After;
import org.junit.Before;

public class StackTraceTest extends AbstractTestCase {

    VanillaRmiServer<String> _server;
    CachedDataSocketFactory _dsf;

    @Before
    public void setUp() throws Exception {
        // start simple server first
        _server = Proxies.newServer("server", 9876, "ServiceObject");

        // "client" connections
        MetaClasses meta = new MetaClasses(Classes.getClassLoader(getClass()));
        _dsf = new CachedDataSocketFactory("foo", "localhost:9876", 1000L, meta);
    }

    @After
    public void tearDown() {
        if (_dsf != null) {
            _dsf.close();
        }

        if (_server != null) {
            closeServer(_server);
        }
    }

    public void testResilienceToCorruptedStackTrace() throws Exception {
        // create an exception with missing stack frames
        Exception t = new IllegalArgumentException("oopsie");
        corruptStack(t);

        // get a client connection
        DataSocket ds = _dsf.acquire("new");

        // must not throw
        DataSockets.appendStackTrace(ds, t);
    }

    // just zap the given Throwable's call stack
    private void corruptStack(Throwable t) throws Exception {
        Field stackTrace = Throwable.class.getDeclaredField("stackTrace");
        stackTrace.setAccessible(true);
        stackTrace.set(t, new StackTraceElement[0]);
    }

}
