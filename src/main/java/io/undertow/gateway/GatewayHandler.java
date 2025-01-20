package io.undertow.gateway;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpRequest;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.ByteToMessageDecoder.MERGE_CUMULATOR;

/**
 * Switcher to distinguish different protocols
 */
public class GatewayHandler extends ChannelInboundHandlerAdapter {
    private final ByteToMessageDecoder.Cumulator cumulator = MERGE_CUMULATOR;
    private final ProtocolMatcher[] matcherList;

    private ByteBuf buf;
    private boolean hasData = false;
    private final GatewayCallback gatewayCallback;

    public GatewayHandler(GatewayCallback gatewayCallback, ProtocolMatcher... matchers) {
        this.gatewayCallback = gatewayCallback;
        if (matchers.length == 0) {
            throw new IllegalArgumentException("No matcher for ProtocolDetector");
        }
        this.matcherList = matchers;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf in)) {
            gatewayCallback.log(ctx, "unexpected message type for ProtocolDetector: " + msg.getClass());
            closeOnFlush(ctx.channel());
            return;
        }
        hasData = true;

        if (buf == null) {
            buf = in;
        } else {
            buf = cumulator.cumulate(ctx.alloc(), buf, in);
        }
        boolean hasPending = false;
        for (ProtocolMatcher matcher : matcherList) {
            int match = matcher.match(ctx, buf.duplicate());
            if (match == ProtocolMatcher.MATCH) {
                matcher.handlePipeline(ctx, ctx.pipeline());
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(buf);
                return;
            }

            if (match == ProtocolMatcher.PENDING) {
                gatewayCallback.log(ctx, "match pending..");
                hasPending = true;
            }
        }
        if (hasPending) {
            return;
        }
        // all miss
        gatewayCallback.onAllMatchMiss(ctx, buf);
        closeOnFlush(ctx.channel());
        buf = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (buf != null) {
            buf.release();
            buf = null;
        }
        if (!hasData && cause instanceof IOException) {
            // 有LBS负载均衡的服务，通过探测端口是否开启来判断服务是否存活，
            // 他们只开启端口，然后就会关闭隧道，此时这里就会有IOException: java.io.IOException: Connection reset by peer
            gatewayCallback.log(ctx, "exception: " + cause.getClass() + " ->" + cause.getMessage() + " before any data receive");
        } else {
            gatewayCallback.log(ctx, "protocol detect error", cause);
        }
        closeOnFlush(ctx.channel());
    }

    private static void closeOnFlush(Channel channel) {
        if (channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    public interface GatewayCallback {

        void onChannelInit(Channel channel);

        void onAllMatchMiss(ChannelHandlerContext ctx, ByteBuf buf);

        void enterUndertowWebServer(ChannelHandlerContext ctx,HttpRequest httpRequest);

        void onServletDispatch(ChannelHandlerContext ctx, String requestURL);

        void log(ChannelHandlerContext ctx, String msg);

        void log(ChannelHandlerContext ctx, String msg, Throwable cause);
    }

    /**
     * 二进制协议识别，如ssl、http、ssh、socks代理、smtp等五层协议
     */
    public interface ProtocolMatcher {

        int MATCH = 1;
        int MISMATCH = -1;
        int PENDING = 0;

        /**
         * If match the protocol.
         *
         * @return 1:match, -1:not match, 0:still can not judge now
         */
        int match(ChannelHandlerContext context, ByteBuf buf);

        /**
         * Deal with the pipeline when matched
         */
        void handlePipeline(ChannelHandlerContext context, ChannelPipeline pipeline);

        /**
         * @deprecated because of typo
         */
        @Deprecated
        static void slowAttachDetect(ChannelHandlerContext ctx, Class<? extends ChannelHandler> middleHandlerClass,
                                     long timeout) {
            slowAttackDetect(ctx, middleHandlerClass, timeout);
        }

        static void slowAttackDetect(ChannelHandlerContext ctx, Class<? extends ChannelHandler> middleHandlerClass,
                                     long timeout) {
            WeakReference<Channel> ref = new WeakReference<>(ctx.channel());
            ctx.executor().schedule(() -> {
                Channel ch = ref.get();
                if (ch == null) {
                    // this request handle completed already
                    return;
                }
                ChannelHandler handler = ch.pipeline().get(middleHandlerClass);
                if (handler != null) {
                    ctx.fireExceptionCaught(new IOException(middleHandlerClass + " meet slow attack"));
                }
            }, timeout, TimeUnit.MILLISECONDS);
        }
    }


    /**
     * 希望在netty层面处理的http请求，
     * <ul>
     *     <li>websocket这种需要重度依赖异步网络模型，而使用tomcat这种传统服务模型不好处理</li>
     *     <li>考虑极致性能，明确可以使用纯异步的来支持的部分http请求</li>
     * </ul>
     */
    public interface NettyHttpMatcher {
        boolean match(HttpRequest httpRequest);

        void handlePipeline(ChannelHandlerContext context, ChannelPipeline pipeline);

        static boolean isWebSocket(HttpRequest httpRequest) {
            return "websocket".equalsIgnoreCase(httpRequest.headers().get("Upgrade"));
        }
    }

}
