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
import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;

import org.freshvanilla.net.CachedDataSocketFactory;
import org.freshvanilla.test.AbstractTestCase;
import org.freshvanilla.utils.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmiFailureTest extends AbstractTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(RmiFailureTest.class.getName());

    interface Server {
        public void throwsRuntimeException();

        public void throwsException() throws BackingStoreException;

        public int add(int a, int b);

        public String concat(String a, String b);
    }

    interface FaultyClient extends Server {
        public void serverDoesntImpliment();

        public void serverDoesntImpliment(Callback<Void> callback);

        public void add(int a, int b, Callback<Integer> callback);

        public double add(double a, double b);

        public BigInteger concat(BigInteger a, BigInteger b);

        // not implemented on the server.
        public String concat(String a, String b, String c);
    }

    static class FaultyServer implements Server {
        public void throwsRuntimeException() {
            throw new UnsupportedOperationException("org.freshvanilla.test exception");
        }

        public void throwsException() throws BackingStoreException {
            throw new BackingStoreException("org.freshvanilla.test exception");
        }

        public int add(int a, int b) {
            return a + b;
        }

        public String concat(String a, String b) {
            return a + b;
        }
    }

    public void test_faultyServer() throws IOException {
        final VanillaRmiServer<FaultyServer> server = Proxies.newServer(getName(), 0, new FaultyServer());
        CachedDataSocketFactory factory = null;

        try {
            factory = new CachedDataSocketFactory("mx-client", "localhost:" + server.getPort(), getMetaClasses());
            @SuppressWarnings("unchecked")
            FaultyClient client = Proxies.newClient(factory, FaultyClient.class);

            assertEquals(10, client.add(1, 9));
            CallbackQueue<Integer> cq = new CallbackQueue<Integer>();

            for (int i = 1; i <= 101; i++) {
                client.add(i, 9, cq);
            }

            for (int i = 10; i <= 110; i++) {
                assertEquals(i, (int)cq.take(1000));
            }

            try {
                client.throwsRuntimeException();
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException expected) {
                // expected
            }

            try {
                client.throwsException();
                fail("Expected BackingStoreException");
            }
            catch (BackingStoreException expected) {
                // expected
            }

            try {
                client.serverDoesntImpliment();
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException expected) {
                // expected
            }

            try {
                CallbackQueue<Void> cq2 = new CallbackQueue<Void>();
                client.serverDoesntImpliment(cq2);
                fail("Expected UnsupportedOperationException, got= " + cq2.take(1000));
            }
            catch (UnsupportedOperationException expected) {
                // expected
            }

            try {
                CallbackQueue<Void> callback = new CallbackQueue<Void>();
                client.serverDoesntImpliment(callback);
                callback.take(1000);
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException expected) {
                // expected
            }

            assertEquals(10.0, client.add(1.5, 9.5));
            assertEquals(7.0, client.add(2.0, 5.0));
            assertEquals("Hi there", client.concat("Hi", client.concat(" ", "there")));
            assertEquals(new BigInteger("1234567890"),
                client.concat(new BigInteger("12345"), new BigInteger("67890")));

            try {
                fail("Expected UnsupportedOperationException, got= " + client.concat("Hi", " ", "there"));
            }
            catch (UnsupportedOperationException expected) {
                // expected
            }
        }
        finally {
            if (factory != null) {
                factory.close();
            }
            server.close();
        }
    }

    public void test_absentServer() throws MalformedURLException {
        CachedDataSocketFactory factory = null;

        try {
            factory = new CachedDataSocketFactory("mx-client", "localhost:" + 12345, 5000L, getMetaClasses());
            @SuppressWarnings("unchecked")
            FaultyClient client = Proxies.newClient(factory, FaultyClient.class);

            try {
                fail("Expected UndeclaredThrowableException, got= " + client.add(1, 2));
            }
            catch (UndeclaredThrowableException expected) {
                assertEquals(java.net.ConnectException.class, expected.getCause().getClass());
            }
        }
        finally {
            if (factory != null) {
                factory.close();
            }
        }
    }

    public void test_oneAbsentServer() throws IOException, InterruptedException {
        final VanillaRmiServer<FaultyServer> server = Proxies.newServer(getName(), 0, new FaultyServer());
        CachedDataSocketFactory factory = null;

        try {
            factory = new CachedDataSocketFactory("mx-client", "localhost:54321,localhost:"
                                                               + server.getPort(), 5000L, getMetaClasses());
            @SuppressWarnings("unchecked")
            FaultyClient client = Proxies.newClient(factory, FaultyClient.class);
            @SuppressWarnings("unchecked")
            FaultyClient client2 = Proxies.newClient(factory, FaultyClient.class);
            int tests = isFullBuild() ? 100001 : 10001;

            // warm up.
            for (int i = 0; i < 10 * 1000; i++) {
                assertEquals(i + 1, client.add(1, i));
                assertEquals(i + 2, client2.add(2, i));
            }
            // end of warm up.

            long start2 = System.nanoTime();
            for (int i = 0; i < 99; i++) {
                System.nanoTime();
            }

            long adjust = (System.nanoTime() - start2) / 100;
            Thread.sleep(250);

            long[] testResults = new long[tests];

            for (int j = 0; j < tests; j++) {
                long start = System.nanoTime();

                assertEquals(j + 100, client.add(100, j));
                assertEquals(j + 200, client2.add(200, j));
                assertEquals(j + 300, client.add(300, j));
                assertEquals(j + 400, client2.add(400, j));

                long time = System.nanoTime() - start;

                testResults[j] = (time - adjust) / 4;

                if (tests % 25 == 0) {
                    Thread.sleep(1);
                }
            }

            Arrays.sort(testResults);

            for (int i : new int[]{50, 75, 80, 85, 90, 92, 95, 98, 99}) {
                LOG.info(i + " % - " + testResults[(int)(0.5 + testResults.length * i / 100.0)] / 100 / 10.0
                         + " micro-second per call.");
            }
        }
        finally {
            if (factory != null) {
                factory.close();
            }
            server.close();
        }
    }

    public void test_oneAbsentAsyncServer() throws IOException, InterruptedException {
        final VanillaRmiServer<FaultyServer> server = Proxies.newServer(getName(), 0, new FaultyServer());
        CachedDataSocketFactory factory = null;

        try {
            factory = new CachedDataSocketFactory("mx-client", "localhost:54321,localhost:"
                                                               + server.getPort(), 5000L, getMetaClasses());
            @SuppressWarnings("unchecked")
            FaultyClient client = Proxies.newClient(factory, FaultyClient.class);
            @SuppressWarnings("unchecked")
            FaultyClient client2 = Proxies.newClient(factory, FaultyClient.class);
            int tests = isFullBuild() ? 5001 : 501;

            // warm up.
            final CallbackQueue<Boolean> cqX = new CallbackQueue<Boolean>(20 * 1000);

            for (int i = 0; i < 10 * 1000; i++) {
                client.add(11, i, new ExpectCallback(cqX, i + 11));
                client2.add(222, i, new ExpectCallback(cqX, i + 222));
            }

            for (int i = 0; i < 20 * 1000; i++) {
                assertTrue(cqX.take(1000));
            }

            // end of warm up.

            long start2 = System.nanoTime();
            for (int i = 0; i < 99; i++) {
                System.nanoTime();
            }

            final long adjust = (System.nanoTime() - start2) / 100;
            Thread.sleep(250);

            int runLength = 20;
            long[] testResults = new long[tests * runLength];
            CallbackQueue<Integer> cq = new CallbackQueue<Integer>(runLength);
            CallbackQueue<Integer> cq2 = new CallbackQueue<Integer>(runLength);
            int ptr = 0;

            for (int j = 1; j <= tests; j++) {
                int len = j % runLength + 1;

                for (int i = 0; i < len; i++) {
                    long start = System.nanoTime() + adjust;

                    client.add(2, i, cq);
                    client2.add(17, i, cq2);

                    testResults[ptr++] = (System.nanoTime() - start);
                }

                for (int i = 0; i < len; i++) {
                    assertEquals(2 + i, (int)cq.take(1000));
                    assertEquals(17 + i, (int)cq2.take(1000));
                }

                Thread.sleep(1);
            }

            long[] testResults2 = new long[ptr];
            System.arraycopy(testResults, 0, testResults2, 0, ptr);
            testResults = testResults2;
            Arrays.sort(testResults);

            for (int i : new int[]{50, 75, 80, 85, 90, 92, 95, 98, 99}) {
                LOG.info(i + " % - " + 2 * 1000 * 1000 * 1000
                         / testResults[(int)(0.5 + testResults.length * i / 100.0)] + " per second.");
            }
        }
        finally {
            if (factory != null) {
                factory.close();
            }
            server.close();
        }
    }

    private static class CallbackQueue<T> implements Callback<T> {
        public final BlockingQueue<Object> queue;

        public CallbackQueue() {
            queue = new LinkedBlockingQueue<Object>();
        }

        public CallbackQueue(int maxSize) {
            queue = new ArrayBlockingQueue<Object>(maxSize);
        }

        public void onCallback(T t) throws Exception {
            queue.add(t);
        }

        public void onException(Throwable t) {
            queue.add(t);
        }

        public T take(long timeoutMS) {
            final Object o;

            try {
                o = queue.poll(timeoutMS, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                throw new AssertionError(e);
            }

            if (o instanceof RuntimeException) {
                throw ((RuntimeException)o);
            }

            if (o instanceof Error) {
                throw ((Error)o);
            }

            if (o instanceof Throwable) {
                throw new AssertionError(o);
            }

            @SuppressWarnings("unchecked")
            T result = (T)o;
            return result;
        }
    }

    private static class ExpectCallback implements Callback<Integer> {
        private final CallbackQueue<Boolean> cq2;
        private final int i1;

        public ExpectCallback(CallbackQueue<Boolean> cq2, int i1) {
            this.cq2 = cq2;
            this.i1 = i1;
        }

        public void onCallback(Integer integer) throws Exception {
            cq2.onCallback(integer == i1);
        }

        public void onException(Throwable t) {
            cq2.onException(t);
        }
    }

}
