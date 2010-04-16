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
package org.freshvanilla.collection;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.freshvanilla.rmi.PrimitivePojo;
import org.freshvanilla.rmi.Proxies;
import org.freshvanilla.rmi.VanillaRmiServer;
import org.freshvanilla.test.AbstractTestCase;

public class RmiMapTest extends AbstractTestCase {

    public static void test_map_over_rmi() throws IOException {
        ConcurrentMap<Integer, PrimitivePojo> map = new ConcurrentHashMap<Integer, PrimitivePojo>();

        int size = 10;
        for (int i = 0; i < size; i++) {
            map.put(i, createPojo(i));
        }

        VanillaRmiServer<ConcurrentMap<Integer, PrimitivePojo>> server = Proxies.newServer("test_queue_over_rmi", 0, map);
        ConcurrentMap<Integer, PrimitivePojo> client = null;

        try {
            client = Proxies.newClient("test_queue_over_rmi-client", "localhost:" + server.getPort(), ConcurrentMap.class);

            assertEquals(size, client.size());

            assertEquals(false, client.isEmpty());

            assertEquals(false, client.containsKey(-1));

            final PrimitivePojo pojo_1 = createPojo(-1);

            assertEquals(false, client.containsValue(pojo_1));

            client.put(-1, pojo_1);

            assertEquals(true, client.containsKey(-1));
            assertEquals(true, client.containsValue(pojo_1));

            assertEquals(pojo_1, client.get(-1));

            assertEquals(pojo_1, client.remove(-1));

            assertEquals(null, client.get(-1));

            // Bulk Operations
            Map<Integer, PrimitivePojo> map2 = new LinkedHashMap<Integer, PrimitivePojo>(map);

            client.clear();
            assertTrue(client.isEmpty());

            client.putAll(map2);

            // Views
            // Set<K> keySet();
            assertEquals(map.keySet(), client.keySet());

            // Collection<V> values();
            assertEquals(new ArrayList<PrimitivePojo>(map.values()), client.values());

            // Set<Map.Entry<K, V>> entrySet();
            assertEquals(map.entrySet(), client.entrySet());

            // Comparison and hashing
            assertEquals(client, map);
            assertEquals(map, client);
            assertEquals(client.hashCode(), map.hashCode());
        } finally {
            if (client != null) {
                ((Closeable) client).close();
            }
            if (server != null) {
                server.close();
            }
        }
    }

    private static PrimitivePojo createPojo(int i) {
        return new PrimitivePojo(true, (byte) 1, (short) 2, '3', i, 5.0f, i, 7.0d);
    }

}
