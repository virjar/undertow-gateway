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

package io.undertow.server.handlers;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.undertow.UndertowLogger;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.NetworkUtils;

/**
 * Handler that sets the peer address to the value of the X-Forwarded-For header.
 * <p>
 * This should only be used behind a proxy that always sets this header, otherwise it
 * is possible for an attacker to forge their peer address;
 *
 * @author Stuart Douglas
 */
public class ProxyPeerAddressHandler implements HttpHandler {

    private static final Pattern IP4_EXACT = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");

    private static final Pattern IP6_EXACT = Pattern.compile("(?:[a-zA-Z0-9]{1,4}:){7}[a-zA-Z0-9]{1,4}");

    private final HttpHandler next;

    public ProxyPeerAddressHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String forwardedFor = exchange.requestHeaders().get(HttpHeaderNames.X_FORWARDED_FOR);
        if (forwardedFor != null) {
            String remoteClient = mostRecent(forwardedFor);
            //we have no way of knowing the port
            if(IP4_EXACT.matcher(forwardedFor).matches()) {
                exchange.setSourceAddress(new InetSocketAddress(NetworkUtils.parseIpv4Address(remoteClient), 0));
            } else if(IP6_EXACT.matcher(forwardedFor).matches()) {
                exchange.setSourceAddress(new InetSocketAddress(NetworkUtils.parseIpv6Address(remoteClient), 0));
            } else {
                exchange.setSourceAddress(InetSocketAddress.createUnresolved(remoteClient, 0));
            }
        }
        String forwardedProto = exchange.requestHeaders().get(HttpHeaderNames.X_FORWARDED_PROTO);
        if (forwardedProto != null) {
            exchange.setRequestScheme(mostRecent(forwardedProto));
        }
        String forwardedHost = exchange.requestHeaders().get(HttpHeaderNames.X_FORWARDED_HOST);
        String forwardedPort = exchange.requestHeaders().get(HttpHeaderNames.X_FORWARDED_PORT);
        if (forwardedHost != null) {
            String value = mostRecent(forwardedHost);
            if(value.startsWith("[")) {
                int end = value.lastIndexOf("]");
                if(end == -1 ) {
                    end = 0;
                }
                int index = value.indexOf(":", end);
                if(index != -1) {
                    forwardedPort = value.substring(index + 1);
                    value = value.substring(0, index);
                }
            } else {
                int index = value.lastIndexOf(":");
                if(index != -1) {
                    forwardedPort = value.substring(index + 1);
                    value = value.substring(0, index);
                }
            }
            int port = 0;
            String hostHeader = NetworkUtils.formatPossibleIpv6Address(value);
            if(forwardedPort != null) {
                try {
                    port = Integer.parseInt(mostRecent(forwardedPort));
                    if(port > 0) {
                        String scheme = exchange.getRequestScheme();

                        if (!standardPort(port, scheme)) {
                            hostHeader += ":" + port;
                        }
                    } else {
                        UndertowLogger.REQUEST_LOGGER.debugf("Ignoring negative port: %s", forwardedPort);
                    }
                } catch (NumberFormatException ignore) {
                    UndertowLogger.REQUEST_LOGGER.debugf("Cannot parse port: %s", forwardedPort);
                }
            }
            exchange.requestHeaders().set(HttpHeaderNames.HOST, hostHeader);
            exchange.setDestinationAddress(InetSocketAddress.createUnresolved(value, port));
        }
        next.handleRequest(exchange);
    }

    private String mostRecent(String header) {
        int index = header.indexOf(',');
        if (index == -1) {
            return header;
        } else {
            return header.substring(0, index);
        }
    }

    private static boolean standardPort(int port, String scheme) {
        return (port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme));
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "proxy-peer-address";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new ProxyPeerAddressHandler(handler);
        }
    }
}
