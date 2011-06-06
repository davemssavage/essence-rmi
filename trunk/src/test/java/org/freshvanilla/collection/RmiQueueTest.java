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

package org.freshvanilla.collection;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.freshvanilla.rmi.Proxies;
import org.freshvanilla.rmi.VanillaRmiServer;
import org.freshvanilla.test.AbstractTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RmiQueueTest extends AbstractTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(RmiQueueTest.class.getName());

    public void test_queue_over_rmi() throws IOException, InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<Integer>(10);
        final VanillaRmiServer<BlockingQueue<Integer>> server = Proxies.newServer("test_queue_over_rmi", 0,
            queue);

        try {
            final String serverURL = "localhost:" + server.getPort();

            @SuppressWarnings("unchecked")
            Thread producer = new Thread(new Runnable() {
                public void run() {
                    BlockingQueue<Integer> client = null;

                    try {
                        client = Proxies.newClient("test_queue_over_rmi-producer", serverURL,
                            BlockingQueue.class);

                        for (int i = 0; i < 100; i++) {
                            assertTrue(client.offer(i));
                            Thread.sleep(5);
                        }
                    }
                    catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    finally {
                        closeClient(client);
                    }
                }
            });

            producer.start();

            final AtomicInteger passed = new AtomicInteger();
            final AtomicBoolean finished = new AtomicBoolean();

            @SuppressWarnings("unchecked")
            Thread consumer = new Thread(new Runnable() {
                public void run() {
                    BlockingQueue<Integer> client = null;

                    try {
                        client = Proxies.newClient("test_queue_over_rmi-consumer", serverURL,
                            BlockingQueue.class);

                        for (int i = 0; i < 1000; i++) {
                            final Integer integer = client.poll(200, TimeUnit.MILLISECONDS);
                            if (integer == null) {
                                break;
                            }
                            assertEquals(i, (int)integer);
                            passed.incrementAndGet();
                        }
                        finished.set(true);
                    }
                    catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    finally {
                        closeClient(client);
                    }
                }
            });

            consumer.start();
            producer.join(5000);
            consumer.join(5000);
            assertEquals(100, passed.get());
            assertTrue(finished.get());
        }
        finally {
            if (server != null) {
                server.close();
            }
        }
    }

    public static void test_queue_over_rmi4() throws IOException, InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<Integer>(10);
        final VanillaRmiServer<BlockingQueue<Integer>> server = Proxies.newServer("test_queue_over_rmi", 0,
            queue);

        try {
            final String serverURL = "localhost:" + server.getPort();

            @SuppressWarnings("unchecked")
            final Runnable target = new Runnable() {
                public void run() {
                    BlockingQueue<Integer> client = null;
                    try {
                        client = Proxies.newClient("test_queue_over_rmi4-producer", serverURL,
                            BlockingQueue.class);

                        for (int i = 0; i < 250; i++) {
                            assertTrue(client.offer(i, 200, TimeUnit.MILLISECONDS));
                            Thread.sleep(5);
                        }
                    }
                    catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    finally {
                        closeClient(client);
                    }
                }
            };

            Thread[] threads = new Thread[8];
            for (int i = 0; i < 4; i++) {
                threads[i] = new Thread(target);
            }

            final AtomicInteger passed = new AtomicInteger();
            final AtomicInteger finished = new AtomicInteger();

            @SuppressWarnings("unchecked")
            final Runnable ctarget = new Runnable() {
                public void run() {
                    BlockingQueue<Integer> client = null;

                    try {
                        client = Proxies.newClient("test_queue_over_rmi4-consumer", serverURL,
                            BlockingQueue.class);

                        for (int i = 0; i < 1000; i++) {
                            final Integer integer = client.poll(200, TimeUnit.MILLISECONDS);
                            if (integer == null) {
                                LOG.debug("Got " + i + " tasks.");
                                break;
                            }
                            passed.incrementAndGet();
                        }
                        finished.incrementAndGet();
                    }
                    catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    finally {
                        closeClient(client);
                    }
                }
            };

            for (int i = 4; i < 8; i++) {
                threads[i] = new Thread(ctarget);
            }

            for (int i = 0; i < 8; i++) {
                threads[i].start();
            }

            for (int i = 0; i < 8; i++) {
                threads[i].join(5000);
            }

            assertEquals(1000, passed.get());
            assertEquals(4, finished.get());
        }
        finally {
            closeServer(server);
        }
    }

}
