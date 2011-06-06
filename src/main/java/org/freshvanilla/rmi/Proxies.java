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

import java.io.IOException;
import java.lang.reflect.Proxy;

import org.freshvanilla.lang.MetaClasses;
import org.freshvanilla.net.CachedDataSocketFactory;
import org.freshvanilla.net.DataSocket;
import org.freshvanilla.utils.Classes;
import org.freshvanilla.utils.Factory;

public class Proxies {

    public static <P> VanillaRmiServer<P> newServer(String name, int port, P provider) throws IOException {
        return new VanillaRmiServer<P>(name, port, provider);
    }

    public static <P> VanillaRmiServer<P> newServer(String name, int port, P provider, ClassLoader classLoader)
        throws IOException {
        return new VanillaRmiServer<P>(name, port, provider, classLoader);
    }

    public static <I> I newClient(String name, String connectionString, Class<I>... interfaces) {
        return newClient(name, connectionString, Classes.getClassLoader(interfaces[0]), interfaces);
    }

    public static <I> I newClient(String name,
                                  String connectionString,
                                  ClassLoader classLoader,
                                  Class<I>... interfaces) {
        MetaClasses metaClasses = new MetaClasses(classLoader);
        Factory<String, DataSocket> factory = new CachedDataSocketFactory(name, connectionString, 60 * 1000L,
            metaClasses);
        return newClient(factory, true, classLoader, interfaces);
    }

    public static <I> I newClient(Factory<String, DataSocket> factory, Class<I>... interfaces) {
        return newClient(factory, false, Classes.getClassLoader(interfaces[0]), interfaces);
    }

    @SuppressWarnings("unchecked")
    public static <I> I newClient(Factory<String, DataSocket> factory,
                                  boolean closeFactory,
                                  ClassLoader classLoader,
                                  Class<I>... interfaces) {
        RmiInvocationHandler rmiih = new RmiInvocationHandler(factory, closeFactory);
        return (I)Proxy.newProxyInstance(classLoader, interfaces, rmiih);
    }

}
