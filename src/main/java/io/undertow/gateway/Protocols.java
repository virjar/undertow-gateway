package io.undertow.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class Protocols {

    /**
     * 请注意，用户不要直接继承本class直线协议探测，他是为undertow直接服务的
     */
    public abstract static class HttpPlain implements GatewayHandler.ProtocolMatcher {
        private static final Set<String> methods = new HashSet<>() {{
            addAll(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "DELETE", "TRACE"));
        }};


        @Override
        public MATCH_STATUS match(ChannelHandlerContext context, ByteBuf buf) {
            if (buf.readableBytes() < 8) {
                return MATCH_STATUS.PENDING;
            }

            int index = buf.indexOf(0, 8, (byte) ' ');
            if (index < 0) {
                return MATCH_STATUS.MISMATCH;
            }

            int firstURIIndex = index + 1;
            if (buf.readableBytes() < firstURIIndex + 1) {
                return MATCH_STATUS.PENDING;
            }

            String method = buf.toString(0, index, US_ASCII);
            char firstURI = (char) (buf.getByte(firstURIIndex + buf.readerIndex()) & 0xff);
            if (!methods.contains(method) || firstURI != '/') {
                return MATCH_STATUS.MISMATCH;
            }

            return MATCH_STATUS.MATCH;
        }
    }

    /**
     * Matcher for plain http proxy request.
     */
    public abstract static class HttpProxy implements GatewayHandler.ProtocolMatcher {
        private static final Set<String> methods = new HashSet<>() {{
            addAll(List.of("GET", "POST", "PUT", "HEAD", "OPTIONS", "PATCH", "DELETE",
                    "TRACE"));
        }};


        @Override
        public MATCH_STATUS match(ChannelHandlerContext context, ByteBuf buf) {
            if (buf.readableBytes() < 8) {
                return MATCH_STATUS.PENDING;
            }

            int index = buf.indexOf(0, 8, (byte) ' ');
            if (index < 0) {
                return MATCH_STATUS.MISMATCH;
            }

            int firstURIIndex = index + 1;
            if (buf.readableBytes() < firstURIIndex + 1) {
                return MATCH_STATUS.PENDING;
            }

            String method = buf.toString(0, index, US_ASCII);
            char firstURI = (char) (buf.getByte(firstURIIndex + buf.readerIndex()) & 0xff);
            if (!methods.contains(method) || firstURI == '/') {
                return MATCH_STATUS.MISMATCH;
            }

            return MATCH_STATUS.MATCH;
        }
    }


    public abstract static class HttpsProxy implements GatewayHandler.ProtocolMatcher {

        @Override
        public MATCH_STATUS match(ChannelHandlerContext context, ByteBuf buf) {
            if (buf.readableBytes() < 8) {
                return MATCH_STATUS.PENDING;
            }

            String method = buf.toString(0, 8, US_ASCII);
            if (!"CONNECT ".equalsIgnoreCase(method)) {
                return MATCH_STATUS.MISMATCH;
            }

            return MATCH_STATUS.MATCH;
        }
    }

    /**
     * Matcher for socks5 proxy protocol
     */
    public static abstract class Socks5 implements GatewayHandler.ProtocolMatcher {

        @Override
        public MATCH_STATUS match(ChannelHandlerContext context, ByteBuf buf) {
            if (buf.readableBytes() < 2) {
                return MATCH_STATUS.PENDING;
            }
            byte first = buf.getByte(buf.readerIndex());
            byte second = buf.getByte(buf.readerIndex() + 1);
            if (first == 5) {
                return MATCH_STATUS.MATCH;
            }
            return MATCH_STATUS.MISMATCH;
        }
    }

    public abstract static class SSL implements GatewayHandler.ProtocolMatcher {

        @Override
        public MATCH_STATUS match(ChannelHandlerContext context, ByteBuf buf) {
            if (buf.readableBytes() < 3) {
                return MATCH_STATUS.PENDING;
            }
            byte first = buf.getByte(buf.readerIndex());
            byte second = buf.getByte(buf.readerIndex() + 1);
            byte third = buf.getByte(buf.readerIndex() + 2);
            if (first == 22 && second <= 3 && third <= 3) {
                return MATCH_STATUS.MATCH;
            }
            return MATCH_STATUS.MISMATCH;
        }
    }


}
