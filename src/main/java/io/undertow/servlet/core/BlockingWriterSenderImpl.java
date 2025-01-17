/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

import jakarta.servlet.DispatcherType;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.IoUtils;

/**
 * A sender that uses a print writer.
 *
 * In general this should never be used. It exists for the edge case where a filter has called
 * getWriter() and then the default servlet is being used to serve a text file.
 *
 * @author Stuart Douglas
 */
public class BlockingWriterSenderImpl implements Sender {

    /**
     * TODO: we should be used pooled buffers
     */
    public static final int BUFFER_SIZE = 128;

    private final Charset charset;
    private final HttpServerExchange exchange;
    private final PrintWriter writer;

    private RandomAccessFile pendingFile;
    private boolean inCall;
    private String next;
    private IoCallback queuedCallback;

    public BlockingWriterSenderImpl(final HttpServerExchange exchange, final PrintWriter writer, final String charset) {
        this.exchange = exchange;
        this.writer = writer;
        this.charset = Charset.forName(charset);
    }

    @Override
    public void send(final ByteBuf buffer, final IoCallback callback) {
        if (inCall) {
            queue(new ByteBuf[]{buffer}, callback);
            return;
        }
        if (writeBuffer(buffer, callback)) {
            invokeOnComplete(callback);
        }
    }


    @Override
    public void send(final ByteBuf[] buffer, final IoCallback callback) {
        if (inCall) {
            queue(buffer, callback);
            return;
        }
        for (ByteBuf b : buffer) {
            if (!writeBuffer(b, callback)) {
                return;
            }
        }
        invokeOnComplete(callback);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        if (inCall) {
            queue(data, callback);
            return;
        }
        writer.write(data);

        if (writer.checkError()) {
            callback.onException(exchange, this, new IOException());
        } else {
            invokeOnComplete(callback);
        }
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        if (inCall) {
            queue(new ByteBuf[]{Unpooled.copiedBuffer(data, charset)}, callback);
            return;
        }
        writer.write(data);
        if (writer.checkError()) {
            callback.onException(exchange, this, new IOException());
        } else {
            invokeOnComplete(callback);
        }
    }

    @Override
    public void transferFrom(RandomAccessFile source, IoCallback callback) {
        if (inCall) {
            queue(source, callback);
            return;
        }
        performTransfer(source, callback);
    }

    @Override
    public void transferFrom(RandomAccessFile channel, long start, long length, IoCallback callback) {
        throw new RuntimeException("NYI");
    }

    private void performTransfer(RandomAccessFile source, IoCallback callback) {
        throw new RuntimeException("NYI");
//
//        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
//        try {
//            long pos = source.position();
//            long size = source.size();
//            while (size - pos > 0) {
//                int ret = source.read(buffer);
//                if (ret <= 0) {
//                    break;
//                }
//                pos += ret;
//                buffer.flip();
//                if (!writeBuffer(buffer, callback)) {
//                    return;
//                }
//                buffer.clear();
//            }
//
//            if (pos != size) {
//                throw new EOFException("Unexpected EOF reading file");
//            }
//
//        } catch (IOException e) {
//            callback.onException(exchange, this, e);
//        }
//        invokeOnComplete(callback);
    }


    @Override
    public void close(final IoCallback callback) {
        writer.close();
        invokeOnComplete(callback);
    }

    @Override
    public void close() {
        if(exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getDispatcherType() != DispatcherType.INCLUDE) {
            IoUtils.safeClose(writer);
        }
    }


    private boolean writeBuffer(final ByteBuf buffer, final IoCallback callback) {
        StringBuilder builder = new StringBuilder();
        builder.append(buffer.readCharSequence(buffer.readableBytes(), charset));
        String data = builder.toString();
        writer.write(data);
        if (writer.checkError()) {
            callback.onException(exchange, this, new IOException());
            return false;
        }
        return true;
    }


    private void invokeOnComplete(final IoCallback callback) {
        inCall = true;
        try {
            callback.onComplete(exchange, this);
        } finally {
            inCall = false;
        }
        while (next != null) {
            String next = this.next;
            IoCallback queuedCallback = this.queuedCallback;
            this.next = null;
            this.queuedCallback = null;
            writer.write(next);
            if (writer.checkError()) {
                queuedCallback.onException(exchange, this, new IOException());
            } else {
                inCall = true;
                try {
                    queuedCallback.onComplete(exchange, this);
                } finally {
                    inCall = false;
                }
            }
        }
    }

    private void queue(final ByteBuf[] byteBuffers, final IoCallback ioCallback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null || pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        StringBuilder builder = new StringBuilder();
        for (ByteBuf buffer : byteBuffers) {
            builder.append(buffer.readCharSequence(buffer.readableBytes(), charset));
        }
        this.next = builder.toString();
        queuedCallback = ioCallback;
    }

    private void queue(final String data, final IoCallback callback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null || pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        next = data;
        queuedCallback = callback;
    }
    private void queue(final RandomAccessFile data, final IoCallback callback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null || pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        pendingFile = data;
        queuedCallback = callback;
    }

}
