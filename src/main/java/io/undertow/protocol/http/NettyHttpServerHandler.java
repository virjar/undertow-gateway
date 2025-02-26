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

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.gateway.GatewayHandler;
import io.undertow.server.ConnectionSSLSessionInfo;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.HttpProtocolNames;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final ExecutorService blockingExecutor;
    private final HttpHandler rootHandler;
    private final SSLEngine engine;
    private final int bufferSize;
    private final boolean directBuffers;


    private HttpServerConnection connection;
    private boolean expectingEmpty;
    private final GatewayHandler.GatewayCallback gatewayCallback;

    public NettyHttpServerHandler(ExecutorService blockingExecutor, HttpHandler rootHandler,
                                  SSLEngine engine, int bufferSize, boolean directBuffers,
                                  GatewayHandler.GatewayCallback gatewayCallback) {
        this.blockingExecutor = blockingExecutor;
        this.rootHandler = rootHandler;
        this.engine = engine;
        this.bufferSize = bufferSize;
        this.directBuffers = directBuffers;
        this.gatewayCallback = gatewayCallback;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }


    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            AtomicReference<HttpRequest> request = new AtomicReference<>((HttpRequest) msg);
            if (connection == null) {
                connection = new HttpServerConnection(ctx, blockingExecutor, engine == null ? null : new ConnectionSSLSessionInfo(engine.getSession()),
                        bufferSize, directBuffers, gatewayCallback);
                ctx.channel().closeFuture().addListener(future -> {
                    connection.closed(new ClosedChannelException());
                });
            }
            HttpServerExchange exchange = new HttpServerExchange(connection, request.get().headers());
            Connectors.setExchangeRequestPath(exchange, request.get().uri(), "UTF-8", true, false, new StringBuilder());
            exchange.requestMethod(request.get().method().name());
            if (engine == null) {
                exchange.setRequestScheme("http");
            } else {
                exchange.setRequestScheme("https");
            }
            exchange.protocol(HttpProtocolNames.HTTP_1_1);
            if (msg instanceof HttpContent) {
                connection.addData((HttpContent) msg);
            }
            if (!request.get().headers().contains(HttpHeaderNames.CONTENT_LENGTH) &&
                    !request.get().headers().contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                expectingEmpty = true;
                Connectors.terminateRequest(exchange);
            } else if (msg instanceof LastHttpContent) {
                Connectors.terminateRequest(exchange);
            }

            connection.newExchange(exchange, rootHandler);
        } else if (msg instanceof HttpContent) {
            if (expectingEmpty) {
                expectingEmpty = false;
            } else {
                connection.addData((HttpContent) msg);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        gatewayCallback.log(ctx, "netty handler caught exception", cause);
        connection.closed(new IOException(cause));
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            gatewayCallback.log(ctx, "read timeout:" + event);
            exceptionCaught(ctx, new TimeoutException("read timeout"));
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}
