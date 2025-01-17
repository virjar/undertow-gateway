/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.protocol.http;

import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import io.undertow.server.HttpHandler;

/**
 * 这是undertow的流程，因为需要实现对websocket的托管，所以需要侵入到http协议的识别中途，本类不再使用
 *
 * @deprecated
 */
@Deprecated
public class NettyHttpServerInitializer extends ChannelInitializer<SocketChannel> {

    private final ExecutorService blockingExecutor;
    private final HttpHandler rootHandler;
    private final SSLContext sslCtx;
    private final int bufferSize;
    private final boolean directBuffers;

    public NettyHttpServerInitializer(ExecutorService blockingExecutor, HttpHandler rootHandler, SSLContext sslCtx, int bufferSize, boolean directBuffers) {
        this.blockingExecutor = blockingExecutor;
        this.rootHandler = rootHandler;
        this.sslCtx = sslCtx;
        this.bufferSize = bufferSize;
        this.directBuffers = directBuffers;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        SSLEngine engine = null;
        if (sslCtx != null) {
            SSLEngine sslEngine = sslCtx.createSSLEngine();
            sslEngine.setUseClientMode(false);
            SslHandler sslHandler = new SslHandler(sslEngine);
            engine = sslHandler.engine();
            p.addLast(sslHandler);
        }
        p.addLast(new HttpServerCodec());
        p.addLast(new NettyHttpServerHandler(blockingExecutor, rootHandler, engine, bufferSize, directBuffers));
    }
}
