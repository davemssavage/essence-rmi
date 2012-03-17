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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freshvanilla.test.AbstractTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchmarkTest extends AbstractTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkTest.class.getName());

    private static final NumberFormat NF = new DecimalFormat();
    private static final int RUNS = 1001;

    public void test_collectionLatency() throws IOException, InterruptedException, ClassNotFoundException {

        final int size = 5000;
        long start, time;

        start = System.nanoTime();
        for (int i = 0; i < size; i++) {
            new Integer(i);
        }
        time = System.nanoTime() - start;
        LOG.info("Integer construction time = " + (time / size) + " ns.");

        PrimitivePojo[] list2 = new PrimitivePojo[size];
        start = System.nanoTime();
        for (int i = 0; i < size; i++) {
            list2[i] = new PrimitivePojo(true, (byte)1, (short)2, '3', i, 5.0f, 6L, 7.0d);
        }
        time = System.nanoTime() - start;
        assertNotNull(list2[0]);
        LOG.info("PrimitivePojo add time = " + (time / size) + " ns.");

        List<PrimitivePojo> primitivePojos = new ArrayList<PrimitivePojo>(size);
        List<WrapperPojo> wrapperPojos = new ArrayList<WrapperPojo>(size);
        start = System.nanoTime();
        for (int i = 0; i < size; i++) {
            primitivePojos.add(new PrimitivePojo(true, (byte)1, (short)2, '3', i, 5.0f, i, 7.0d));
            wrapperPojos.add(new WrapperPojo(i % 2 == 0, (byte)i, (short)i, (char)i, i, (float)i, (long)i,
                (double)i, String.valueOf(i)));
        }
        time = System.nanoTime() - start;
        LOG.info("pojo construction time = " + (time / size / 2) + " ns.");
        assertEquals(size, primitivePojos.size());

        for (int i = 0; i <= 5; i++) {
            // Java Serialization timings.
            start = System.nanoTime();
            List<PrimitivePojo> listB = fromBytes(toBytes(primitivePojos));
            List<PrimitivePojo> list2B = fromBytes(toBytes(wrapperPojos));
            time = System.nanoTime() - start;
            assertNotNull(listB);
            assertNotNull(list2B);
            if (i > 0) {
                LOG.info(i + ": pojo Java serialization time = " + (time / size / 2) + " ns per pojo.");
            }
        }

        VanillaRmiServer<ServiceImpl> server = null;
        IService service = null;

        try {
            server = Proxies.newServer(getName(), 0, new ServiceImpl());
            service = Proxies.newClient(getName() + "-clnt", "" + server.getPort(), IService.class);
            for (int i = 0; i <= (isFullBuild() ? 101 : 5); i++) {
                start = System.nanoTime();
                final Object listB = service.echo(primitivePojos);
                final Object list2B = service.echo(wrapperPojos);
                time = System.nanoTime() - start;
                assertEquals(primitivePojos, listB);
                assertEquals(wrapperPojos, list2B);
                if (i > 0) {
                    LOG.info(i + ": Latency for " + primitivePojos.size() + " pojos = " + NF.format(time / 4)
                             + " ns. each way. per pojo = " + NF.format(time / size / 4));
                }
            }
        }
        finally {
            closeClient(service);
            closeServer(server);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T fromBytes(byte[] primBytes) throws IOException, ClassNotFoundException {
        return (T)new ObjectInputStream(new ByteArrayInputStream(primBytes)).readObject();
    }

    private static byte[] toBytes(Object list) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(list);
        oos.close();
        return baos.toByteArray();
    }

    public void test_avg_latency() throws IOException {
        VanillaRmiServer<ServiceImpl> server = null;
        IService service = null;

        try {
            server = Proxies.newServer(getName(), 0, new ServiceImpl());
            service = Proxies.newClient(getName() + "-clnt", "" + server.getPort(), IService.class);
            long start = 0;
            final PrimitivePojo ppojo = new PrimitivePojo(true, (byte)1, (short)2, '3', 4, 5.0f, 6L, 7.0d);
            final WrapperPojo wpojo = new WrapperPojo(true, (byte)1, (short)2, '3', 4, 5.0f, 6L, 7.0d,
                "eight");
            for (int i = 0; i < RUNS * 2; i++) {
                if (i == RUNS) start = System.nanoTime();
                service.empty();
                assertEquals(0, service.prims(false, (byte)0, '\0', (short)0, 0, 0.0f, 0.0));
                assertEquals(7, service.prims(true, (byte)1, '2', (short)3, 4, 5.0f, 6.0));
                assertNull(service.wraps(null, null, null, null, null, null, null));
                assertEquals(0, (int)service.wraps(false, (byte)0, '\0', (short)0, 0, 0.0f, 0.0));
                assertEquals(7, (int)service.wraps(true, (byte)1, '2', (short)3, 4, 5.0f, 6.0));
                assertEquals(0,
                    service.count(Collections.emptySet(), Collections.emptyList(), Collections.emptyMap()));
                assertEquals(getName(), service.echo(getName()));
                assertEquals(ppojo, service.echo(ppojo));
                assertEquals(wpojo, service.echo(wpojo));
            }
            long time = System.nanoTime() - start;
            long latency = time / RUNS / 9;
            LOG.info("Average latency = " + NF.format(latency) + " ns.");
        }
        finally {
            closeClient(service);
            closeServer(server);
        }
    }

    public void test_latency_98perc() throws IOException, InterruptedException {
        VanillaRmiServer<ServiceImpl> server = null;
        IService service = null;

        try {
            server = Proxies.newServer(getName(), 0, new ServiceImpl());
            service = Proxies.newClient(getName() + "-clnt", "localhost:" + server.getPort(), IService.class);
            service.bye("Finished Test");
            assertFalse(server.getProvider().byeCalled);

            long start = 0;

            List<Long> list = new ArrayList<Long>(RUNS * 10);
            List<Long> list2 = new ArrayList<Long>(RUNS);

            final PrimitivePojo ppojo = new PrimitivePojo(true, (byte)1, (short)2, '3', 4, 5.0f, 6L, 7.0d);
            final WrapperPojo wpojo = new WrapperPojo(true, (byte)1, (short)2, '3', 4, 5.0f, 6L, 7.0d,
                "eight");

            for (int i = 0; i < RUNS * 2; i++) {
                final boolean time = i > RUNS;

                if (time) {
                    start = System.nanoTime();
                }

                service.empty();

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(0, service.prims(false, (byte)0, '\0', (short)0, 0, 0.0f, 0.0));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(7, service.prims(true, (byte)1, '2', (short)3, 4, 5.0f, 6.0));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertNull(service.wraps(null, null, null, null, null, null, null));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(0, (int)service.wraps(false, (byte)0, '\0', (short)0, 0, 0.0f, 0.0));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(7, (int)service.wraps(true, (byte)1, '2', (short)3, 4, 5.0f, 6.0));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(0,
                    service.count(Collections.emptySet(), Collections.emptyList(), Collections.emptyMap()));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(getName(), service.echo(getName()));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(ppojo, service.echo(ppojo));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                assertEquals(wpojo, service.echo(wpojo));

                if (time) {
                    list.add(System.nanoTime() - start);
                    start = System.nanoTime();
                }

                service.bye("Sayonara.");

                if (time) {
                    list.add(System.nanoTime() - start);
                }

                // control timer.
                if (time) {
                    start = System.nanoTime();
                }

                assertEquals(getName(), getName());

                if (time) {
                    list2.add(System.nanoTime() - start);
                }

                if (i % 100 == 0) {
                    Thread.sleep(1);
                }
            }

            Collections.sort(list);
            Collections.sort(list2);

            // give the timings compensation for the overhead of the test itself.
            LOG.info("Mid latency = " + NF.format(list.get(list.size() / 2) - list2.get(list2.size() / 2))
                     + " ns.");
            LOG.info("90% latency = "
                     + NF.format(list.get(list.size() * 9 / 10) - list2.get(list2.size() * 9 / 10)) + " ns.");
            LOG.info("98% latency = "
                     + NF.format(list.get(list.size() * 98 / 100) - list2.get(list2.size() * 98 / 100))
                     + " ns.");

        }
        finally {
            if (server == null) {
                fail("Server is null");
            }
            else {
                assertFalse(server.getProvider().byeCalled);
                closeClient(service);
        
                for (int i = 0; i < 10; i++) {
                    if (server.getProvider().byeCalled) {
                        break;
                    }
                    Thread.sleep(50);
                }
        
                assertTrue(server.getProvider().byeCalled);
                closeServer(server);
            }
        }
    }
}

interface IService {
    public int prims(boolean z, byte b, char c, short s, int i, float f, double d);

    public Integer wraps(Boolean z, Byte b, Character c, Short s, Integer i, Float f, Double d);

    public void empty();

    public int count(Set<?> s, List<?> l, Map<?, ?> m);

    public Object echo(Object text);

    @OnDisconnection
    public void bye(String mesg);
}

class ServiceImpl implements IService {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class.getName());

    volatile boolean byeCalled = false;

    public int prims(boolean z, byte b, char c, short s, int i, float f, double d) {
        return (z ? 1 : 0) + (b > 0 ? 1 : 0) + (c > 0 ? 1 : 0) + (s > 0 ? 1 : 0) + (i > 0 ? 1 : 0)
               + (f > 0 ? 1 : 0) + (d > 0 ? 1 : 0);
    }

    public Integer wraps(Boolean z, Byte b, Character c, Short s, Integer i, Float f, Double d) {
        if (z == null || b == null || c == null || s == null || i == null || f == null || d == null) {
            return null;
        }
        return prims(z, b, c, s, i, f, d);
    }

    public void empty() {
        // does nothing.
    }

    public int count(Set<?> s, List<?> l, Map<?, ?> m) {
        return s.size() + l.size() + m.size();
    }

    public Object echo(Object obj) {
        return obj;
    }

    @OnDisconnection
    public void bye(String mesg) {
        LOG.info("Bye - " + mesg);
        byeCalled = true;
    }
}
