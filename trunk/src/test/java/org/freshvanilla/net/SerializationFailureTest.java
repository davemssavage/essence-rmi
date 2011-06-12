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

import java.io.NotSerializableException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.freshvanilla.rmi.Proxies;
import org.freshvanilla.rmi.VanillaRmiServer;
import org.freshvanilla.test.AbstractTestCase;
import org.junit.After;
import org.junit.Before;

public class SerializationFailureTest extends AbstractTestCase {

    VanillaRmiServer<ConcurrentHashMap<String, Object>> _server;
    ConcurrentMap<String, Object> _proxy;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        // start simple server
        _server = Proxies.newServer("server", 9876, new ConcurrentHashMap<String, Object>());

        // "client"
        _proxy = Proxies.newClient("map", "localhost:9876", ConcurrentMap.class);
    }

    @After
    public void tearDown() {
        if (_proxy != null) {
            closeClient(_proxy);
        }

        if (_server != null) {
            closeServer(_server);
        }
    }

    public void testSerializationFailure() throws Exception {
        // make sure Map works
        _proxy.put("key", "value");
        assertEquals("value", _proxy.get("key"));

        // put unserializable value
        try {
            _proxy.put("thread", new Thread());
        }
        catch (UndeclaredThrowableException ute) {
            // expect cause
            assertEquals(NotSerializableException.class, ute.getCause().getClass());
        }
    }

}
