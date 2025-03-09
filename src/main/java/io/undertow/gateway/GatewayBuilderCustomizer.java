package io.undertow.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.undertow.Undertow;
import io.undertow.gateway.springboot.web.embed.UndertowBuilderCustomizer;
import jakarta.servlet.Servlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Servlet.class, Undertow.class, GatewayHandler.class})
public class GatewayBuilderCustomizer implements UndertowBuilderCustomizer {

    private final List<GatewayHandler.ProtocolMatcher> protocolMatchers;

    private final List<GatewayHandler.GatewayCallback> gatewayCallbacks;

    private final List<GatewayHandler.NettyHttpMatcher> nettyHttpMatchers;

    @Autowired
    public GatewayBuilderCustomizer(List<GatewayHandler.ProtocolMatcher> protocolMatchers,
                                    List<GatewayHandler.GatewayCallback> gatewayCallbacks,
                                    List<GatewayHandler.NettyHttpMatcher> nettyHttpMatchers
    ) {
        this.protocolMatchers = protocolMatchers;
        this.gatewayCallbacks = gatewayCallbacks;
        this.nettyHttpMatchers = nettyHttpMatchers;
    }


    @Override
    public void customize(Undertow.Builder builder) {
        for (GatewayHandler.ProtocolMatcher protocolMatcher : protocolMatchers) {
            builder.addProtocol(protocolMatcher);
        }
        for (GatewayHandler.NettyHttpMatcher nettyHttpMatcher : nettyHttpMatchers) {
            builder.addWsMatcher(nettyHttpMatcher);
        }
        if (!CollectionUtils.isEmpty(gatewayCallbacks)) {
            builder.setGateWayCallback(new ComposeGatewayCallback());
        }

    }

    private class ComposeGatewayCallback implements GatewayHandler.GatewayCallback {

        @Override
        public void onChannelInit(Channel channel) {
            gatewayCallbacks.forEach(callback -> callback.onChannelInit(channel));
        }

        @Override
        public void onAllMatchMiss(ChannelHandlerContext ctx, ByteBuf buf) {
            gatewayCallbacks.forEach(callback -> callback.onAllMatchMiss(ctx, buf));
        }

        @Override
        public void enterUndertowWebServer(ChannelHandlerContext ctx, HttpRequest httpRequest) {
            gatewayCallbacks.forEach(callback -> callback.enterUndertowWebServer(ctx, httpRequest));
        }

        @Override
        public void onServletDispatch(ChannelHandlerContext ctx, String requestURL) {
            gatewayCallbacks.forEach(callback -> callback.onServletDispatch(ctx, requestURL));
        }

        @Override
        public void log(ChannelHandlerContext ctx, String msg) {
            gatewayCallbacks.forEach(callback -> callback.log(ctx, msg));
        }

        @Override
        public void log(ChannelHandlerContext ctx, String msg, Throwable cause) {
            gatewayCallbacks.forEach(callback -> callback.log(ctx, msg, cause));
        }
    }


}
