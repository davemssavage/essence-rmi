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

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.freshvanilla.net.CachedDataSocketFactory;
import org.freshvanilla.net.DataSocket;
import org.freshvanilla.utils.Factory;
import org.freshvanilla.utils.SimpleResource;

public class Proxies {

    public static <P> VanillaRmiServer<P> newServer(String name, int port, P provider) throws IOException {
        return new VanillaRmiServer<P>(name, port, provider);
    }

    public static <I> I newClient(Factory<String, DataSocket> factory, Class<I>... interfaces) {
        return newClient(factory, false, interfaces);
    }

    public static <I> I newClient(String name, String connectionString, Class<I>... interfaces) {
        final Factory<String, DataSocket> factory = new CachedDataSocketFactory(name, connectionString, 60 * 1000L);
        return newClient(factory, true, interfaces);
    }

    public static <I> I newClient(Factory<String, DataSocket> factory, boolean closeFactory, Class<I>... interfaces) {
        Set<Class<?>> interfaceSet = new LinkedHashSet<Class<?>>(Arrays.asList(interfaces));
        interfaceSet.add(SimpleResource.class);
        final Class<?>[] interfaces2 = interfaceSet.toArray(new Class<?>[interfaceSet.size()]);

        final RmiInvocationHandler rmiih = new RmiInvocationHandler(factory, closeFactory);
        @SuppressWarnings("unchecked")
        I proxy = (I)Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), interfaces2, rmiih);
        return proxy;
    }

}
