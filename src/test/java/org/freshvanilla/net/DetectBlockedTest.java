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
package org.freshvanilla.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedHashMap;

import org.freshvanilla.test.AbstractTestCase;

public class DetectBlockedTest extends AbstractTestCase {

    public static void test_read() throws IOException, ClassNotFoundException, InterruptedException {
        int port = 23456;
        final InetSocketAddress isa = new InetSocketAddress(port);
        final ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(true);
        ssc.socket().bind(isa);

        Thread t = new Thread(new Runnable() {
            public void run() {
                DataSocket ds = null;
                try {
                    final SocketChannel sc = ssc.accept();
                    Thread.sleep(3 * 1000);
                    final BinaryWireFormat wf = new BinaryWireFormat();
                    ds = new VanillaDataSocket("test", null, sc, wf, new LinkedHashMap<String, Object>(), 1024 * 1024);
                    Thread.sleep(30 * 1000);
                    fail();
                } catch (InterruptedException expected) {
                    // expected
                } catch (Exception e) {
                    // let the test fail
                    throw new RuntimeException(e);
                } finally {
                    if (ds != null) {
                        ds.close();
                    }
                }
            }
        });

        t.start();

        final SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(true);
        sc.socket().connect(isa);

        final BinaryWireFormat wf = new BinaryWireFormat();
        DataSocket ds = new VanillaDataSocket("test", isa, sc, wf, new LinkedHashMap<String, Object>(), 1024 * 1024);

        try {
            for (int i = 0; i < 1000; i++) {
                final ByteBuffer buffer = ds.writeBuffer();
                wf.writeObject(buffer, new byte[100 * 1000]);
                ds.flush();
            }
        } catch (ClosedChannelException expected) {
            // expected.
        }

        t.interrupt();
        t.join();
    }

}
