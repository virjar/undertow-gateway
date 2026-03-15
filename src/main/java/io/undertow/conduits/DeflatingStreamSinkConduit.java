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
package io.undertow.conduits;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.NewInstanceObjectPool;
import io.undertow.util.ObjectPool;
import io.undertow.util.PooledObject;
import org.xnio.conduits.StreamSinkConduit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * A StreamSinkConduit that compresses response data using the deflate algorithm.
 * Uses a pooled Deflater for efficiency.
 */
public class DeflatingStreamSinkConduit implements StreamSinkConduit {

    private final ByteArrayOutputStream baos;
    private final DeflaterOutputStream deflaterStream;
    private final PooledObject<Deflater> pooledDeflater;
    private boolean closed = false;

    public DeflatingStreamSinkConduit(ConduitFactory<StreamSinkConduit> factory,
                                      HttpServerExchange exchange,
                                      ObjectPool<Deflater> deflaterPool) {
        this.pooledDeflater = deflaterPool.allocate();
        this.baos = new ByteArrayOutputStream();
        // syncFlush=true ensures compressed bytes are flushed for each chunk (required for streaming)
        this.deflaterStream = new DeflaterOutputStream(baos, pooledDeflater.getObject(), true);
    }

    @Override
    public ByteBuf process(ByteBuf data, boolean last) throws IOException {
        if (!closed && data != null && data.readableBytes() > 0) {
            byte[] bytes = new byte[data.readableBytes()];
            data.getBytes(data.readerIndex(), bytes);
            deflaterStream.write(bytes);
        }
        if (last && !closed) {
            closed = true;
            deflaterStream.close();
        } else if (!closed) {
            deflaterStream.flush();
        }
        byte[] out = baos.toByteArray();
        baos.reset();
        return out.length > 0 ? Unpooled.wrappedBuffer(out) : Unpooled.EMPTY_BUFFER;
    }

    @Override
    public void dispose() {
        pooledDeflater.close();
    }

    /**
     * Creates an ObjectPool of Deflater instances at the specified compression level.
     */
    public static ObjectPool<Deflater> newInstanceDeflaterPool(int level) {
        return new NewInstanceObjectPool<>(
                () -> new Deflater(level),
                Deflater::end
        );
    }
}
