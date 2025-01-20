package io.undertow;

import io.undertow.gateway.GatewayHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.undertow.protocol.http.NettyHttpServerHandler;
import io.undertow.server.HttpHandler;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

public class GatewayHttpInitializer extends SimpleChannelInboundHandler<HttpObject> {
    private Queue<HttpObject> httpObjects;

    private final GatewayHandler.GatewayCallback gatewayCallback;
    private final List<GatewayHandler.NettyHttpMatcher> nettyHttpMatchers;


    // param from io.undertow.protocol.http.NettyHttpServerInitializer
    private final ExecutorService blockingExecutor;
    private final HttpHandler rootHandler;
    private final int bufferSize;
    private final boolean directBuffers;


    public GatewayHttpInitializer(GatewayHandler.GatewayCallback gatewayCallback, List<GatewayHandler.NettyHttpMatcher> nettyHttpMatchers, ExecutorService blockingExecutor, HttpHandler rootHandler, int bufferSize, boolean directBuffers) {
        this.gatewayCallback = gatewayCallback;
        this.nettyHttpMatchers = nettyHttpMatchers;
        this.blockingExecutor = blockingExecutor;
        this.rootHandler = rootHandler;
        this.bufferSize = bufferSize;
        this.directBuffers = directBuffers;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (!(msg instanceof HttpRequest httpRequest)) {
            httpObjects.add(msg);
            // pending??
            return;
        }
        if (httpObjects != null) {
            for (HttpObject httpObject : httpObjects) {
                ReferenceCountUtil.release(httpObject);
            }
        }
        httpObjects = new ArrayDeque<>();
        httpObjects.add(msg);
        gatewayCallback.log(ctx, "http request: " + httpRequest);
        ChannelPipeline pipeline = ctx.pipeline();
        if (!nettyHttpHook(ctx, httpRequest)) {
            // add by virjar: undertow没有加超时控制，发现客户端有连接keep-alive，但是没有主动关闭，导致连接存在泄漏问题
            // 不过超时本身不太好设计，所以这里直接搞一个10分钟没有读取的超时信号
            pipeline.addFirst(new IdleStateHandler(600, 0, 0));
            NettyHttpServerHandler nettyHttpServerHandler = new NettyHttpServerHandler(blockingExecutor, rootHandler, null,
                    bufferSize, directBuffers, gatewayCallback);
            pipeline.addLast(nettyHttpServerHandler);
            gatewayCallback.enterUndertowWebServer(ctx, httpRequest);
        }
        pipeline.remove(this);
    }

    private boolean nettyHttpHook(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        for (GatewayHandler.NettyHttpMatcher nettyHttpMatcher : nettyHttpMatchers) {
            if (!nettyHttpMatcher.match(httpRequest)) {
                continue;
            }
            gatewayCallback.log(ctx, "netty serve request detected");
            // websocket请求，在netty层面处理，因为tomcat几乎没有能力处理websocket，其网络api就是原生的二进制流
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(
                    new IdleStateHandler(45, 30, 0),
                    new HttpObjectAggregator(1 << 25)
            );
            nettyHttpMatcher.handlePipeline(ctx, pipeline);
            return true;
        }
        return false;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (httpObjects != null) {
            HttpObject b;
            while ((b = httpObjects.poll()) != null) {
                ctx.fireChannelRead(b);
            }
        }
        ctx.flush();
        httpObjects = null;
    }

}