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

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.freshvanilla.utils.Callback;
import org.freshvanilla.utils.NamedThreadFactory;
import org.freshvanilla.utils.VanillaResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanillaDataSocket extends VanillaResource implements DataSocket {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaDataSocket.class);

    private static final int MIN_PACKET_SIZE = 256;
    private static final int BUFFER_SIZE = 256 * 1024;
    private static final long TIMEOUT_MS = 10 * 1000L;
    private static final long WARNING_PERIOD = 1000L - DataSockets.CHECK_PERIOD_MS / 2;

    private final InetSocketAddress address;
    private final SocketChannel channel;
    private final WireFormat wireFormat;
    private final AtomicLong microTimestamp = new AtomicLong(System.currentTimeMillis() * 1000L);
    private final Object executorLock = new Object();
    private final ConcurrentMap<Long, Callback<?>> callbackMap = new ConcurrentHashMap<Long, Callback<?>>();
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private final Map<String, Object> otherHeader;
    private ExecutorService executor = null;
    // warning metrics.
    private boolean reading = false;
    private long readTimeMS = 0;
    private long nextReadWarningMS = 0;
    private boolean writing = false;
    private long writeTimeMS = 0;
    private long nextWriteWarningMS = 0;

    @SuppressWarnings("unchecked")
    public VanillaDataSocket(String name, InetSocketAddress address,
                             SocketChannel channel, WireFormat wireFormat,
                             Map<String, Object> header, int maximumMessageSize) throws IOException {
        super(name);
        this.address = address;
        this.channel = channel;
        channel.configureBlocking(true);
        Socket socket = channel.socket();
        socket.setTcpNoDelay(true);
        socket.setTrafficClass(/*IPTOS_LOWDELAY*/0x10);
        socket.setSendBufferSize(BUFFER_SIZE);
        socket.setReceiveBufferSize(BUFFER_SIZE);
        this.wireFormat = wireFormat;
        readBuffer = allocateBuffer(maximumMessageSize);
        writeBuffer = allocateBuffer(maximumMessageSize);
        getLog().debug(name + ": connecting to " + socket);
        DataSockets.registerDataSocket(this);

        wireFormat.writeObject(writeBuffer(), header);
        flush();
        final ByteBuffer rb = read();
        otherHeader = (Map<String, Object>) wireFormat.readObject(rb);
        getLog().debug(name + ": connected to " + socket + ' ' + otherHeader);
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    private ByteBuffer allocateBuffer(int maximumMessageSize) {
        // we always use direct buffers.
        return ByteBuffer.allocateDirect(maximumMessageSize);
    }

    public Map<String, Object> getOtherHeader() {
        return otherHeader;
    }

    public WireFormat wireFormat() {
        return wireFormat;
    }

    public void addCallback(long sequenceNumber, Callback<?> callback) {
        callbackMap.put(sequenceNumber, callback);
    }

    public Callback<?> removeCallback(long sequenceNumber) {
        return callbackMap.remove(sequenceNumber);
    }

    public void setReader(final Callback<DataSocket> reader) {
        synchronized (executorLock) {
            ExecutorService executor1 = this.executor;
            if (executor1 != null) {
                return;
            }
            this.executor = Executors.newCachedThreadPool(new NamedThreadFactory(name + "-reply-listener", Thread.MAX_PRIORITY, true));
            executor.submit(new ReaderRunnable(reader));
        }
    }

    public ByteBuffer writeBuffer() {
        writeBuffer.clear();
        // so we can write the length later.
        writeBuffer.position(4);
        return writeBuffer;
    }

    public ByteBuffer read() throws IOException {
        final ByteBuffer rb = readBuffer;
        rb.rewind();
        rb.limit(MIN_PACKET_SIZE);

        readFully(rb);
        reading = true;

        try {
            int len = rb.getInt(0);
            if (len > MIN_PACKET_SIZE) {
                rb.limit(len);
                readFully(rb);
            }
        } finally {
            reading = false;
            readTimeMS = 0;
        }

        rb.rewind();
        // after the length.
        rb.position(4);
        return rb;
    }

    private void readFully(ByteBuffer rb) throws IOException {
        channelRead(rb);

        if (rb.remaining() <= 0) {
            return;
        }

        do {
            channelRead(rb);

            if (rb.remaining() <= 0) {
                return;
            }

            Thread.yield();
        } while (true);
    }

    private void channelRead(ByteBuffer rb) throws IOException {
        int len = -1;

        try {
            len = channel.read(rb);
        } catch (IOException e) {
            final String eStr = e.toString();
            if (!eStr.equals("java.io.IOException: An established connection was aborted by the software in your host machine")
                    && !eStr.equals("java.nio.channels.AsynchronousCloseException")) {
                throw e;
            }
        }

        if (len < 0) {
            throw new EOFException("An established connection was aborted by the software in your host machine");
        }
    }

    protected void writeFully(ByteBuffer wb) throws IOException {
        writeChannel(wb);

        if (wb.remaining() <= 0) {
            return;
        }

        long start = System.currentTimeMillis();
        long next = start + 50;
        int retries = 0;
        while (wb.remaining() > 0) {
            long time = System.currentTimeMillis() - start;
            if (time > next) {
                Thread.yield();
                retries++;
                next += 50 * retries;
            }
            writeChannel(wb);
        }
    }

    private void writeChannel(ByteBuffer wb) throws IOException {
        int len = -1;

        try {
            len = channel.write(wb);

        } catch (IOException e) {
            Class<? extends IOException> eClass = e.getClass();
            if (eClass != IOException.class) {
                throw e;
            }
        }

        if (len < 0) {
            throw new EOFException();
        }
    }

    public void flush() throws IOException {
        final ByteBuffer wb = writeBuffer;
        int len = wb.position();
        wb.flip();
        wb.putInt(0, len);

        if (len < MIN_PACKET_SIZE) {
            wb.limit(len = MIN_PACKET_SIZE);
        }

        writing = true;

        try {
            writeFully(wb);
        } finally {
            writing = false;
            writeTimeMS = 0;
        }
    }

    public long microTimestamp() {
        return microTimestamp.getAndIncrement();
    }

    public void close() {
        super.close();
        DataSockets.unregisterDataSocket(this);

        try {
            channel.close();
        } catch (IOException ignored) {
            // ignored.
        }

        if (executor != null) {
            executor.shutdownNow();
        }

        for (Callback<?> callback : callbackMap.values()) {
            callback.onException(new IllegalStateException(name + " is closed!"));
        }

        executor = null;
        callbackMap.clear();
    }

    class ReaderRunnable implements Runnable {
        private final Callback<DataSocket> reader;

        ReaderRunnable(Callback<DataSocket> reader) {
            this.reader = reader;
        }

        public void run() {
            while (!isClosed()) {
                try {
                    reader.onCallback(VanillaDataSocket.this);
                } catch (Exception e) {
                    if (isClosed()) {
                        return;
                    }

                    reader.onException(e);
                    if (e instanceof IOException) {
                        close();
                    }
                }
            }
        }
    }

    public void timedCheck(long timeMS) {
        if (reading) {
            if (readTimeMS == 0) {
                readTimeMS = timeMS;
                nextReadWarningMS = timeMS + WARNING_PERIOD;
            } else if (timeMS >= nextReadWarningMS) {
                final long totalWriteTimeMS = timeMS - readTimeMS;
                if (totalWriteTimeMS > TIMEOUT_MS) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(name + ": closing reading connection after " + totalWriteTimeMS + " ms");
                    }
                    close();
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(name + ": waiting for long running read " + totalWriteTimeMS + " ms");
                    }
                    nextReadWarningMS = timeMS + WARNING_PERIOD;
                }
            }
        }

        if (writing) {
            if (writeTimeMS == 0) {
                writeTimeMS = timeMS;
                nextWriteWarningMS = timeMS + WARNING_PERIOD;
            } else if (timeMS >= nextWriteWarningMS) {
                final long totalWriteTimeMS = timeMS - writeTimeMS;
                if (totalWriteTimeMS > TIMEOUT_MS) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(name + ": closing writing connection after " + totalWriteTimeMS + " ms");
                    }
                    close();
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(name + ": waiting for long running write " + totalWriteTimeMS + " ms");
                    }
                    nextWriteWarningMS = timeMS + WARNING_PERIOD;
                }
            }
        }
    }

}

