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

package io.undertow;

import io.undertow.gateway.GatewayHandler;
import io.undertow.gateway.Protocols;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import io.undertow.util.UndertowOption;
import io.undertow.util.UndertowOptionMap;
import org.jboss.logging.Logger;
import org.xnio.Option;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Convenience class used to build an Undertow server.
 * <p>
 *
 * @author Stuart Douglas
 */
public final class Undertow {

    private final int ioThreads;
    private final int workerThreads;
    private final List<ListenerConfig> listeners = new ArrayList<>();
    private volatile List<ListenerInfo> listenerInfo;
    private final HttpHandler rootHandler;
    private final UndertowOptionMap workerOptions;
    private final UndertowOptionMap socketOptions;
    private final UndertowOptionMap serverOptions;
    private final int bufferSize;
    private final boolean directBuffers;

    /**
     * Will be true when a {@link XnioWorker} instance was NOT provided to the {@link Builder}.
     * When true, a new worker will be created during {@link Undertow#start()},
     * and shutdown when {@link Undertow#stop()} is called.
     * <p>
     * Will be false when a {@link XnioWorker} instance was provided to the {@link Builder}.
     * When false, the provided {@link #worker} will be used instead of creating a new one in {@link Undertow#start()}.
     * Also, when false, the {@link #worker} will NOT be shutdown when {@link Undertow#stop()} is called.
     */
    private final boolean internalWorker;

    private ExecutorService worker;
    private EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;
    List<Channel> channels;
    private final List<GatewayHandler.ProtocolMatcher> protocolMatchers;
    private final List<GatewayHandler.NettyHttpMatcher> nettyHttpMatchers;
    private final GatewayHandler.GatewayCallback gatewayCallback;

    private Undertow(Builder builder) {
        this.ioThreads = builder.ioThreads;
        this.workerThreads = builder.workerThreads;
        this.listeners.addAll(builder.listeners);
        this.rootHandler = builder.handler;
        this.worker = builder.worker;
        this.bufferSize = builder.bufferSize;
        this.directBuffers = builder.directBuffers;
        this.internalWorker = builder.worker == null;
        this.workerOptions = builder.workerOptions.getMap();
        this.socketOptions = builder.socketOptions.getMap();
        this.serverOptions = builder.serverOptions.getMap();
        this.protocolMatchers = builder.protocolMatchers;
        this.nettyHttpMatchers = builder.nettyHttpMatchers;
        this.gatewayCallback = builder.callback;
    }

    /**
     * @return A builder that can be used to create an Undertow server instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public synchronized void start() {
        UndertowLogger.ROOT_LOGGER.debugf("starting undertow server %s", this);
        try {

            if (internalWorker) {
                worker = Executors.newFixedThreadPool(workerThreads);
            }
            // Configure SSL.
            // Configure the server.
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            channels = new ArrayList<>();
            listenerInfo = new ArrayList<>();
            for (ListenerConfig listener : listeners) {
                UndertowLogger.ROOT_LOGGER.debugf("Configuring listener with protocol %s for interface %s and port %s", listener.type, listener.host, listener.port);
                final HttpHandler rootHandler = listener.rootHandler != null ? listener.rootHandler : this.rootHandler;
                if (listener.type == ListenerType.AJP) {
                    throw new RuntimeException("NYI");
//                    AjpOpenListener openListener = new AjpOpenListener(buffers, serverOptions);
//                    openListener.setRootHandler(rootHandler);
//
//                    final ChannelListener<StreamConnection> finalListener;
//                    if (listener.useProxyProtocol) {
//                        finalListener = new ProxyProtocolOpenListener(openListener, null, buffers, UndertowOptionMap.EMPTY);
//                    } else {
//                        finalListener = openListener;
//                    }
//                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(finalListener);
//                    OptionMap.Builder builder = OptionMap.builder().addAll(socketOptions);
//
//                    for (Map.Entry<UndertowOption<?>, Object> i : listener.overrideSocketOptions) {
//                        builder.set((Option) XnioUndertowOptions.key(i.getKey()), i.getValue());
//                    }
//                    OptionMap socketOptionsWithOverrides = builder.getMap();
//                    AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), acceptListener, socketOptionsWithOverrides);
//                    server.resumeAccepts();
//                    channels.add(server);
//                    listenerInfo.add(new ListenerInfo("ajp", server.getLocalAddress(), openListener, null, server));
                } else if (listener.type == ListenerType.HTTP || listener.type == ListenerType.HTTPS) {
                    ArrayList<GatewayHandler.ProtocolMatcher> matchers = new ArrayList<>();
                    // http 协议需要直接放到第一个，这样用户扩展就只能实现其他协议，无法干预http协议的动作
                    matchers.add(new Protocols.HttpPlain() {
                        @Override
                        public void handlePipeline(ChannelHandlerContext context, ChannelPipeline pipeline) {
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new GatewayHttpInitializer(gatewayCallback, nettyHttpMatchers, worker, rootHandler, bufferSize, directBuffers));

                            GatewayHandler.ProtocolMatcher.slowAttachDetect(context, GatewayHttpInitializer.class, 60_000);
                        }
                    });
//                    matchers.add(new Protocols.SSL() {
//
//                        @Override
//                        public void handlePipeline(ChannelHandlerContext context, ChannelPipeline pipeline) {
//                            SSLContext sslCtx;
//                            if (listener.sslContext != null) {
//                                sslCtx = listener.sslContext;
//                            } else {
//                                sslCtx = SSLContext.getInstance("TLS");
//                                sslCtx.init(listener.keyManagers, listener.trustManagers, new SecureRandom());
//                            }
//                            pipeline.addLast(sslContext.newHandler(pipeline.channel().alloc()));
//                            pipeline.addLast(new GatewayHandler(gatewayCallback, protocolMatchers.toArray(new GatewayHandler.ProtocolMatcher[]{})));
//                        }
//                    });

                    matchers.addAll(protocolMatchers);
                    GatewayHandler.ProtocolMatcher[] lowLevelMatchers = matchers.toArray(new GatewayHandler.ProtocolMatcher[]{});

                    Channel ch = bootstrap()
                            //.childHandler(new NettyHttpServerInitializer(worker, rootHandler, null, bufferSize, directBuffers))
                            .childHandler(new ChannelInitializer<>() {
                                @Override
                                protected void initChannel(Channel ch) throws Exception {
                                    gatewayCallback.onChannelInit(ch);
                                    GatewayHandler gatewayHandler = new GatewayHandler(gatewayCallback, lowLevelMatchers);
                                    ch.pipeline().addLast(gatewayHandler);
                                }
                            })
                            .bind(listener.host, listener.port).sync().channel();

                    channels.add(ch);
                    listenerInfo.add(new ListenerInfo("http", ch.localAddress(), null));
                }
//                else if (listener.type == ListenerType.HTTPS) {
//
//                    SSLContext sslCtx;
//                    if (listener.sslContext != null) {
//                        sslCtx = listener.sslContext;
//                    } else {
//                        sslCtx = SSLContext.getInstance("TLS");
//                        sslCtx.init(listener.keyManagers, listener.trustManagers, new SecureRandom());
//                    }
//                    Channel ch = bootstrap()
//                            .childHandler(new NettyHttpServerInitializer(worker, rootHandler, sslCtx, bufferSize, directBuffers))
//                            .bind(listener.host, listener.port).sync().channel();
//
//                    channels.add(ch);
//                    listenerInfo.add(new ListenerInfo("https", ch.localAddress(), null));
//                }
            }

        } catch (Exception e) {
            if (internalWorker && worker != null) {
                worker.shutdownNow();
            }
            throw new RuntimeException(e);
        }
    }


    private ServerBootstrap bootstrap() {
        ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
        return new ServerBootstrap()
                .option(ChannelOption.ALLOCATOR, allocator)
                .option(ChannelOption.SO_BACKLOG, socketOptions.get(UndertowOptions.BACKLOG, 1024))
                .option(ChannelOption.SO_REUSEADDR, socketOptions.get(UndertowOptions.REUSE_ADDRESSES, true))
                .childOption(ChannelOption.ALLOCATOR, allocator)
                .childOption(ChannelOption.SO_KEEPALIVE, socketOptions.get(UndertowOptions.KEEP_ALIVE, false))
                .childOption(ChannelOption.TCP_NODELAY, socketOptions.get(UndertowOptions.TCP_NODELAY, true))
                // Requires EpollServerSocketChannel
//                .childOption(EpollChannelOption.TCP_CORK, socketOptions.get(UndertowOptions.CORK, true))
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class);
    }


    public synchronized void stop() {
        if (bossGroup == null) {
            return;
        }
        UndertowLogger.ROOT_LOGGER.debugf("stopping undertow server %s", this);
        if (channels != null) {
            for (Channel channel : channels) {
                channel.close();
            }
            channels = null;
        }

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();

        /*
         * Only shutdown the worker if it was created during start()
         */
        if (internalWorker && worker != null) {
            Integer shutdownTimeoutMillis = serverOptions.get(UndertowOptions.SHUTDOWN_TIMEOUT);
            worker.shutdown();
            try {
                if (shutdownTimeoutMillis == null) {
                    //worker.awaitTermination();
                } else {
                    if (!worker.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                        worker.shutdownNow();
                    }
                }
            } catch (InterruptedException e) {
                worker.shutdownNow();
                throw new RuntimeException(e);
            }
            worker = null;
        }
        listenerInfo = null;
    }

    public ExecutorService getWorker() {
        return worker;
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public List<ListenerInfo> getListenerInfo() {
        if (listenerInfo == null) {
            throw UndertowMessages.MESSAGES.serverNotStarted();
        }
        return Collections.unmodifiableList(listenerInfo);
    }


    public enum ListenerType {
        HTTP,
        HTTPS,
        AJP
    }

    private static class ListenerConfig {

        final ListenerType type;
        final int port;
        final String host;
        final KeyManager[] keyManagers;
        final TrustManager[] trustManagers;
        final SSLContext sslContext;
        final HttpHandler rootHandler;
        final UndertowOptionMap overrideSocketOptions;
        final boolean useProxyProtocol;

        private ListenerConfig(final ListenerType type, final int port, final String host, KeyManager[] keyManagers, TrustManager[] trustManagers, HttpHandler rootHandler) {
            this.type = type;
            this.port = port;
            this.host = host;
            this.keyManagers = keyManagers;
            this.trustManagers = trustManagers;
            this.rootHandler = rootHandler;
            this.sslContext = null;
            this.overrideSocketOptions = UndertowOptionMap.EMPTY;
            this.useProxyProtocol = false;
        }

        private ListenerConfig(final ListenerType type, final int port, final String host, SSLContext sslContext, HttpHandler rootHandler) {
            this.type = type;
            this.port = port;
            this.host = host;
            this.rootHandler = rootHandler;
            this.keyManagers = null;
            this.trustManagers = null;
            this.sslContext = sslContext;
            this.overrideSocketOptions = UndertowOptionMap.EMPTY;
            this.useProxyProtocol = false;
        }

        private ListenerConfig(final ListenerBuilder listenerBuilder) {
            this.type = listenerBuilder.type;
            this.port = listenerBuilder.port;
            this.host = listenerBuilder.host;
            this.rootHandler = listenerBuilder.rootHandler;
            this.keyManagers = listenerBuilder.keyManagers;
            this.trustManagers = listenerBuilder.trustManagers;
            this.sslContext = listenerBuilder.sslContext;
            this.overrideSocketOptions = listenerBuilder.overrideSocketOptions;
            this.useProxyProtocol = listenerBuilder.useProxyProtocol;
        }
    }

    public static final class ListenerBuilder {

        ListenerType type;
        int port;
        String host;
        KeyManager[] keyManagers;
        TrustManager[] trustManagers;
        SSLContext sslContext;
        HttpHandler rootHandler;
        UndertowOptionMap overrideSocketOptions = UndertowOptionMap.EMPTY;
        boolean useProxyProtocol;

        public ListenerBuilder setType(ListenerType type) {
            this.type = type;
            return this;
        }

        public ListenerBuilder setPort(int port) {
            this.port = port;
            return this;
        }

        public ListenerBuilder setHost(String host) {
            this.host = host;
            return this;
        }

        public ListenerBuilder setKeyManagers(KeyManager[] keyManagers) {
            this.keyManagers = keyManagers;
            return this;
        }

        public ListenerBuilder setTrustManagers(TrustManager[] trustManagers) {
            this.trustManagers = trustManagers;
            return this;
        }

        public ListenerBuilder setSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public ListenerBuilder setRootHandler(HttpHandler rootHandler) {
            this.rootHandler = rootHandler;
            return this;
        }

        public ListenerBuilder setOverrideSocketOptions(UndertowOptionMap overrideSocketOptions) {
            this.overrideSocketOptions = overrideSocketOptions;
            return this;
        }

        public ListenerBuilder setUseProxyProtocol(boolean useProxyProtocol) {
            this.useProxyProtocol = useProxyProtocol;
            return this;
        }
    }

    public static final class Builder {

        int bufferSize;
        int ioThreads;
        int workerThreads;
        boolean directBuffers;
        final List<ListenerConfig> listeners = new ArrayList<>();
        final List<GatewayHandler.ProtocolMatcher> protocolMatchers = new ArrayList<>();
        final List<GatewayHandler.NettyHttpMatcher> nettyHttpMatchers = new ArrayList<>();
        GatewayHandler.GatewayCallback callback = new DefaultGatewayCallback();

        HttpHandler handler;
        ExecutorService worker;

        final UndertowOptionMap.Builder workerOptions = UndertowOptionMap.builder();
        final UndertowOptionMap.Builder socketOptions = UndertowOptionMap.builder();
        final UndertowOptionMap.Builder serverOptions = UndertowOptionMap.builder();

        private Builder() {
            ioThreads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
            workerThreads = ioThreads * 8;
            long maxMemory = Runtime.getRuntime().maxMemory();
            //smaller than 64mb of ram we use 512b buffers
            if (maxMemory < 64 * 1024 * 1024) {
                //use 512b buffers
                directBuffers = false;
                bufferSize = 512;
            } else if (maxMemory < 128 * 1024 * 1024) {
                //use 1k buffers
                directBuffers = true;
                bufferSize = 1024;
            } else {
                //use 16k buffers for best performance
                //as 16k is generally the max amount of data that can be sent in a single write() call
                directBuffers = true;
                bufferSize = 1024 * 16 - 20; //the 20 is to allow some space for protocol headers, see UNDERTOW-1209
            }

        }

        public Undertow build() {
            return new Undertow(this);
        }

        @Deprecated
        public Builder addListener(int port, String host) {
            listeners.add(new ListenerConfig(ListenerType.HTTP, port, host, null, null, null));
            return this;
        }

        @Deprecated
        public Builder addListener(int port, String host, ListenerType listenerType) {
            listeners.add(new ListenerConfig(listenerType, port, host, null, null, null));
            return this;
        }

        public Builder addListener(ListenerBuilder listenerBuilder) {
            listeners.add(new ListenerConfig(listenerBuilder));
            return this;
        }

        public Builder addHttpListener(int port, String host) {
            listeners.add(new ListenerConfig(ListenerType.HTTP, port, host, null, null, null));
            return this;
        }

        public Builder addHttpsListener(int port, String host, KeyManager[] keyManagers, TrustManager[] trustManagers) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, keyManagers, trustManagers, null));
            return this;
        }

        public Builder addHttpsListener(int port, String host, SSLContext sslContext) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, sslContext, null));
            return this;
        }

        public Builder addAjpListener(int port, String host) {
            listeners.add(new ListenerConfig(ListenerType.AJP, port, host, null, null, null));
            return this;
        }

        public Builder addHttpListener(int port, String host, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.HTTP, port, host, null, null, rootHandler));
            return this;
        }

        public Builder addHttpsListener(int port, String host, KeyManager[] keyManagers, TrustManager[] trustManagers, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, keyManagers, trustManagers, rootHandler));
            return this;
        }

        public Builder addHttpsListener(int port, String host, SSLContext sslContext, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, sslContext, rootHandler));
            return this;
        }

        public Builder addAjpListener(int port, String host, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.AJP, port, host, null, null, rootHandler));
            return this;
        }

        public Builder addProtocol(GatewayHandler.ProtocolMatcher protocolMatcher) {
            protocolMatchers.add(protocolMatcher);
            return this;
        }

        public Builder addWsMatcher(GatewayHandler.NettyHttpMatcher nettyHttpMatcher) {
            nettyHttpMatchers.add(nettyHttpMatcher);
            return this;
        }

        public Builder setGateWayCallback(GatewayHandler.GatewayCallback callback) {
            this.callback = callback;
            return this;
        }

        public Builder setBufferSize(final int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        @Deprecated
        public Builder setBuffersPerRegion(final int buffersPerRegion) {
            return this;
        }

        public Builder setIoThreads(final int ioThreads) {
            this.ioThreads = ioThreads;
            return this;
        }

        public Builder setWorkerThreads(final int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        public Builder setDirectBuffers(final boolean directBuffers) {
            this.directBuffers = directBuffers;
            return this;
        }

        public Builder setHandler(final HttpHandler handler) {
            this.handler = handler;
            return this;
        }

        public <T> Builder setServerOption(final Option<T> option, final T value) {
            serverOptions.set((UndertowOption<? super T>) option, value);
            return this;
        }

        public <T> Builder setSocketOption(final Option<T> option, final T value) {
            socketOptions.set((UndertowOption<? super T>) option, value);
            return this;
        }

        public <T> Builder setWorkerOption(final UndertowOption<T> option, final T value) {
            workerOptions.set(option, value);
            return this;
        }

        /**
         * When null (the default), a new {@link XnioWorker} will be created according
         * to the various worker-related configuration (ioThreads, workerThreads, workerOptions)
         * when {@link Undertow#start()} is called.
         * Additionally, this newly created worker will be shutdown when {@link Undertow#stop()} is called.
         * <br/>
         * <p>
         * When non-null, the provided {@link XnioWorker} will be reused instead of creating a new {@link XnioWorker}
         * when {@link Undertow#start()} is called.
         * Additionally, the provided {@link XnioWorker} will NOT be shutdown when {@link Undertow#stop()} is called.
         * Essentially, the lifecycle of the provided worker must be maintained outside of the {@link Undertow} instance.
         */
        public <T> Builder setWorker(ExecutorService worker) {
            this.worker = worker;
            return this;
        }
    }

    public static class ListenerInfo {

        private final String protcol;
        private final SocketAddress address;
        private final OpenListener openListener;
        private volatile boolean suspended = false;

        public ListenerInfo(String protcol, SocketAddress address, OpenListener openListener) {
            this.protcol = protcol;
            this.address = address;
            this.openListener = openListener;
        }

        public String getProtcol() {
            return protcol;
        }

        public SocketAddress getAddress() {
            return address;
        }

        public SSLContext getSslContext() {
            return null;
        }

        public void setSslContext(SSLContext sslContext) {
//sslContext            if (ssl != null) {
//                //just ignore it if this is not a SSL listener
//                ssl.updateSSLContext(sslContext);
//            }
        }

        public synchronized void suspend() {
//            suspended = true;
//            channel.suspendAccepts();
//            CountDownLatch latch = new CountDownLatch(1);
//            //the channel may be in the middle of an accept, we need to close from the IO thread
//            channel.getIoThread().execute(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        openListener.closeConnections();
//                    } finally {
//                        latch.countDown();
//                    }
//                }
//            });
//            try {
//                latch.await();
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
        }

        public synchronized void resume() {
//            suspended = false;
//            channel.resumeAccepts();
        }

        public boolean isSuspended() {
            return suspended;
        }

        public ConnectorStatistics getConnectorStatistics() {
            return openListener.getConnectorStatistics();
        }
//
//        public <T> void setSocketOption(Option<T> option, T value) throws IOException {
//            channel.setOption(option, value);
//        }

        public void setServerOptions(UndertowOptionMap options) {
            openListener.setUndertowOptions(options);
        }

        @Override
        public String toString() {
            return "ListenerInfo{" +
                    "protcol='" + protcol + '\'' +
                    ", address=" + address +
                    ", sslContext=" + getSslContext() +
                    '}';
        }
    }

    private static class DefaultGatewayCallback implements GatewayHandler.GatewayCallback {

        @Override
        public void onChannelInit(Channel channel) {

        }

        @Override
        public void onAllMatchMiss(ChannelHandlerContext ctx, ByteBuf buf) {
            log(ctx, "unknown protocol");
        }

        @Override
        public void log(ChannelHandlerContext ctx, String msg) {
            log(ctx, msg, null);
        }

        @Override
        public void log(ChannelHandlerContext ctx, String msg, Throwable cause) {
            UndertowLogger.ROOT_LOGGER.log(Logger.Level.INFO, msg, cause);
        }
    }
}
