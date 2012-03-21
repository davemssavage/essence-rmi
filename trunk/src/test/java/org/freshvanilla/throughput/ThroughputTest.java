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

package org.freshvanilla.throughput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.freshvanilla.rmi.Proxies;
import org.freshvanilla.rmi.VanillaRmiServer;
import org.freshvanilla.test.AbstractTestCase;
import org.freshvanilla.utils.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThroughputTest extends AbstractTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ThroughputTest.class.getName());

    // the first one is a warmup.
    static final int[] MESSAGE_SIZES = {256, 64, 128, 256, 1024, 4096, 16386};
    static final int MESSAGES = 2 * 1000;

    public void test_echoThroughput() throws IOException, InterruptedException {
        EchoServiceImpl echoService = new EchoServiceImpl();
        VanillaRmiServer<EchoServiceImpl> server = Proxies.newServer("echo-service", 0, echoService);
        doEchoThroughput("localhost:" + server.getPort());
        server.close();
    }

    private void doEchoThroughput(String connectionString) throws InterruptedException, IOException {
        AsyncEchoService client = Proxies.newClient("echo-client", connectionString, AsyncEchoService.class);
        BatchingEchoClient client2 = new BatchingEchoClient(client, 64);

        boolean print = false;
        LOG.info("Warming up first.");

        double product = 1;
        int count = 0;

        for (int messageSize : MESSAGE_SIZES) {
            product *= doSyncTest(client, messageSize, MESSAGES, print);
            product *= doAsyncTest("Async", client, messageSize, MESSAGES * 20, print);
            count += 2;

            if (messageSize <= 4096) {
                product *= doAsyncTest("Batched async", client2, messageSize, MESSAGES * 100, print);
                count++;
            }

            if (!print) {
                print = true;
                product = 1;
                count = 0;
            }
        }

        LOG.info(new Formatter().format("The geo-mean throughput was %,d msg/s",
            (int)Math.pow(product, 1.0 / count)).toString());

        client2.close();
        closeClient(client);
    }

    private long doSyncTest(AsyncEchoService client, int messageSize, int messages, boolean print) {
        byte[] bytes = new byte[messageSize];
        long start = System.nanoTime();

        for (int m = 0; m < messages; m++) {
            byte[] byte2 = client.echo(bytes);
            Assert.assertEquals(messageSize, byte2.length);
        }

        long time = System.nanoTime() - start;

        if (print) {
            long rate = messages * 1000L * 1000 * 1000 / time;
            LOG.debug(new Formatter().format(
                "Sync messages, size %,d, bandwidth= %4.1f MB/s, rate= %,d msg/s", messageSize,
                messageSize * messages * 1000.0 * 1000 * 1000 / time / 1024 / 1024, rate).toString());
            return rate;
        }

        return 1;
    }

    private long doAsyncTest(String type,
                             AsyncEchoService client,
                             final int messageSize,
                             int messages,
                             boolean print) throws InterruptedException {
        byte[] bytes = new byte[messageSize];
        final CountDownLatch counter = new CountDownLatch(messages);

        final Callback<byte[]> callback = new Callback<byte[]>() {
            public void onCallback(byte[] bytes) throws Exception {
                Assert.assertEquals(messageSize, bytes.length);
                counter.countDown();
            }

            public void onException(Throwable t) {
                throw new AssertionError(t);
            }
        };

        long start = System.nanoTime();
        for (int m = 1; m <= messages; m++) {
            client.echo(bytes, callback);
        }

        Assert.assertTrue(counter.await(30, TimeUnit.SECONDS));
        long time = System.nanoTime() - start;

        if (print) {
            long rate = messages * 1000L * 1000 * 1000 / time;
            LOG.info(new Formatter().format("%s messages, size %,d, bandwidth= %4.1f MB/s, rate= %,d msg/s",
                type, messageSize, messageSize * messages * 1000.0 * 1000 * 1000 / time / 1024 / 1024, rate)
                .toString());
            return rate;
        }

        return 1;
    }

    interface AsyncEchoService extends EchoService {
        public <T> void echo(T objects, Callback<T> callback);
    }

    interface EchoService {
        public <T> T echo(T object);
    }

    static class EchoServiceImpl implements EchoService {
        public <T> T echo(T object) {
            return object;
        }
    }

    static class BatchingEchoClient implements AsyncEchoService, Runnable {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final BlockingQueue<Object> queue;
        private final AsyncEchoService service;
        private final int batchSize;
        private Callback<Object> callback = null;

        BatchingEchoClient(AsyncEchoService service, int batchSize) {
            this.service = service;
            this.batchSize = batchSize;
            queue = new ArrayBlockingQueue<Object>(batchSize * 8);
            executor.submit(this);
        }

        @SuppressWarnings("unchecked")
        public <T> void echo(T objects, Callback<T> callback) {
            this.callback = (Callback<Object>)callback;
            try {
                queue.put(objects);
            }
            catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        public <T> T echo(T object) {
            return service.echo(object);
        }

        public void run() {
            try {
                List<Object> objects = new ArrayList<Object>(batchSize);

                Callback<List<Object>> callback2 = new Callback<List<Object>>() {
                    public void onCallback(List<Object> results) throws Exception {
                        for (Object result : results) {
                            try {
                                callback.onCallback(result);
                            }
                            catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        }
                    }

                    public void onException(Throwable t) {
                        callback.onException(t);
                    }
                };

                while (!executor.isShutdown()) {
                    objects.clear();
                    final Object o = queue.take();
                    objects.add(o);
                    queue.drainTo(objects, batchSize - 1);
                    service.echo(objects, callback2);
                }
            }
            catch (Throwable t) {
                if (!executor.isShutdown()) {
                    t.printStackTrace();
                }
            }

        }

        public void close() {
            executor.shutdownNow();
        }
    }
}
