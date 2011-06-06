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

package org.freshvanilla.test;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Proxy;

import junit.framework.TestCase;

import org.freshvanilla.lang.MetaClasses;
import org.freshvanilla.rmi.VanillaRmiServer;
import org.freshvanilla.utils.Classes;

public abstract class AbstractTestCase extends TestCase {

    private final MetaClasses _metaClasses = new MetaClasses(Classes.getClassLoader(getClass()));

    public static void closeClient(Object proxy) {
        if (proxy != null) {
            try {
                Object closeable = Proxy.getInvocationHandler(proxy);
                if (closeable instanceof Closeable) {
                    ((Closeable)closeable).close();
                }
            }
            catch (IOException ioex) {
                throw new RuntimeException(ioex);
            }
        }
    }

    public static void closeServer(VanillaRmiServer<?> server) {
        if (server != null) {
            server.close();
        }
    }

    public boolean isFullBuild() {
        return Boolean.getBoolean("full.build");
    }

    public MetaClasses getMetaClasses() {
        return _metaClasses;
    }

}
