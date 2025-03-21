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

package io.undertow.server;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import io.netty.buffer.ByteBuf;
import io.undertow.UndertowLogger;
import io.undertow.gateway.GatewayHandler;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.LegacyCookieSupport;
import io.undertow.util.ParameterLimitException;
import io.undertow.util.StatusCodes;
import io.undertow.util.URLUtils;
import io.undertow.UndertowOptions;

/**
 * This class provides the connector part of the {@link HttpServerExchange} API.
 * <p>
 * It contains methods that logically belong on the exchange, however should only be used
 * by connector implementations.
 *
 * @author Stuart Douglas
 */
public class Connectors {

    /**
     * Flattens the exchange cookie map into the response header map. This should be called by a
     * connector just before the response is started.
     *
     * @param exchange The server exchange
     */
    public static void flattenCookies(final HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getResponseCookiesInternal();
        boolean enableRfc6265Validation = exchange.getConnection().getUndertowOptions().get(UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, UndertowOptions.DEFAULT_ENABLE_RFC6265_COOKIE_VALIDATION);
        if (cookies != null) {
            for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
                exchange.responseHeaders().add(HttpHeaderNames.SET_COOKIE, getCookieString(entry.getValue(), enableRfc6265Validation));
            }
        }
    }

    public static boolean isRunningHandlerChain(HttpServerExchange exchange) {
        return exchange.getConnection().isExecutingHandlerChain();
    }

    /**
     * Attached buffered data to the exchange. The will generally be used to allow data to be re-read.
     *
     * @param exchange The HTTP server exchange
     * @param buffers  The buffers to attach
     */
    public static void ungetRequestBytes(final HttpServerExchange exchange, ByteBuf buffer) {
        exchange.getConnection().ungetRequestBytes(buffer, exchange);
        exchange.resetRequestChannel();
    }

    public static void terminateRequest(final HttpServerExchange exchange) {
        exchange.terminateRequest();
    }

    public static void terminateResponse(final HttpServerExchange exchange) {
        exchange.terminateResponse();
    }

    private static String getCookieString(final Cookie cookie, boolean enableRfc6265Validation) {
        if (enableRfc6265Validation) {
            return addRfc6265ResponseCookieToExchange(cookie);
        } else {
            switch (LegacyCookieSupport.adjustedCookieVersion(cookie)) {
                case 0:
                    return addVersion0ResponseCookieToExchange(cookie);
                case 1:
                default:
                    return addVersion1ResponseCookieToExchange(cookie);
            }
        }
    }

    public static void setRequestStartTime(HttpServerExchange exchange) {
        exchange.setRequestStartTime(System.nanoTime());
    }

    public static void setRequestStartTime(HttpServerExchange existing, HttpServerExchange newExchange) {
        newExchange.setRequestStartTime(existing.getRequestStartTime());
    }

    private static String addRfc6265ResponseCookieToExchange(final Cookie cookie) {
        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        if (cookie.getValue() != null) {
            header.append(cookie.getValue());
        }
        if (cookie.getPath() != null) {
            header.append("; Path=");
            header.append(cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; Domain=");
            header.append(cookie.getDomain());
        }
        if (cookie.isDiscard()) {
            header.append("; Discard");
        }
        if (cookie.isSecure()) {
            header.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        if (cookie.getMaxAge() != null) {
            if (cookie.getMaxAge() >= 0) {
                header.append("; Max-Age=");
                header.append(cookie.getMaxAge());
            }
            // Microsoft IE and Microsoft Edge don't understand Max-Age so send
            // expires as well. Without this, persistent cookies fail with those
            // browsers. They do understand Expires, even with V1 cookies.
            // So, we add Expires header when Expires is not explicitly specified.
            if (cookie.getExpires() == null) {
                if (cookie.getMaxAge() == 0) {
                    Date expires = new Date();
                    expires.setTime(0);
                    header.append("; Expires=");
                    header.append(DateUtils.toOldCookieDateString(expires));
                } else if (cookie.getMaxAge() > 0) {
                    Date expires = new Date();
                    expires.setTime(expires.getTime() + cookie.getMaxAge() * 1000L);
                    header.append("; Expires=");
                    header.append(DateUtils.toOldCookieDateString(expires));
                }
            }
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toDateString(cookie.getExpires()));
        }
        if (cookie.getComment() != null && !cookie.getComment().isEmpty()) {
            header.append("; Comment=");
            header.append(cookie.getComment());
        }
        if (cookie.isSameSite()) {
            if (cookie.getSameSiteMode() != null && !cookie.getSameSiteMode().isEmpty()) {
                header.append("; SameSite=");
                header.append(cookie.getSameSiteMode());
            } else {
                header.append("; SameSite");
            }
        }
        return header.toString();
    }

    private static String addVersion0ResponseCookieToExchange(final Cookie cookie) {
        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        if (cookie.getValue() != null) {
            LegacyCookieSupport.maybeQuote(header, cookie.getValue());
        }

        if (cookie.getPath() != null) {
            header.append("; path=");
            LegacyCookieSupport.maybeQuote(header, cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; domain=");
            LegacyCookieSupport.maybeQuote(header, cookie.getDomain());
        }
        if (cookie.isSecure()) {
            header.append("; secure");
        }
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toOldCookieDateString(cookie.getExpires()));
        } else if (cookie.getMaxAge() != null) {
            if (cookie.getMaxAge() >= 0) {
                header.append("; Max-Age=");
                header.append(cookie.getMaxAge());
            }
            if (cookie.getMaxAge() == 0) {
                Date expires = new Date();
                expires.setTime(0);
                header.append("; Expires=");
                header.append(DateUtils.toOldCookieDateString(expires));
            } else if (cookie.getMaxAge() > 0) {
                Date expires = new Date();
                expires.setTime(expires.getTime() + cookie.getMaxAge() * 1000L);
                header.append("; Expires=");
                header.append(DateUtils.toOldCookieDateString(expires));
            }
        }
        if (cookie.isSameSite()) {
            if (cookie.getSameSiteMode() != null && !cookie.getSameSiteMode().isEmpty()) {
                header.append("; SameSite=");
                header.append(cookie.getSameSiteMode());
            } else {
                header.append("; SameSite");
            }
        }
        return header.toString();

    }

    private static String addVersion1ResponseCookieToExchange(final Cookie cookie) {

        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        if (cookie.getValue() != null) {
            LegacyCookieSupport.maybeQuote(header, cookie.getValue());
        }
        header.append("; Version=1");
        if (cookie.getPath() != null) {
            header.append("; Path=");
            LegacyCookieSupport.maybeQuote(header, cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; Domain=");
            LegacyCookieSupport.maybeQuote(header, cookie.getDomain());
        }
        if (cookie.isDiscard()) {
            header.append("; Discard");
        }
        if (cookie.isSecure()) {
            header.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        if (cookie.getMaxAge() != null) {
            if (cookie.getMaxAge() >= 0) {
                header.append("; Max-Age=");
                header.append(cookie.getMaxAge());
            }
            // Microsoft IE and Microsoft Edge don't understand Max-Age so send
            // expires as well. Without this, persistent cookies fail with those
            // browsers. They do understand Expires, even with V1 cookies.
            // So, we add Expires header when Expires is not explicitly specified.
            if (cookie.getExpires() == null) {
                if (cookie.getMaxAge() == 0) {
                    Date expires = new Date();
                    expires.setTime(0);
                    header.append("; Expires=");
                    header.append(DateUtils.toOldCookieDateString(expires));
                } else if (cookie.getMaxAge() > 0) {
                    Date expires = new Date();
                    expires.setTime(expires.getTime() + cookie.getMaxAge() * 1000L);
                    header.append("; Expires=");
                    header.append(DateUtils.toOldCookieDateString(expires));
                }
            }
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toDateString(cookie.getExpires()));
        }
        if (cookie.getComment() != null && !cookie.getComment().isEmpty()) {
            header.append("; Comment=");
            LegacyCookieSupport.maybeQuote(header, cookie.getComment());
        }
        if (cookie.isSameSite()) {
            if (cookie.getSameSiteMode() != null && !cookie.getSameSiteMode().isEmpty()) {
                header.append("; SameSite=");
                header.append(cookie.getSameSiteMode());
            } else {
                header.append("; SameSite");
            }
        }
        return header.toString();
    }

    public static void executeRootHandler(final HttpHandler handler, final HttpServerExchange exchange) {
        ServerConnection connection = exchange.getConnection();
        try {
            exchange.getConnection().beginExecutingHandlerChain(exchange);
            handler.handleRequest(exchange);
            exchange.getConnection().endExecutingHandlerChain(exchange);
            boolean resumed = exchange.getConnection().isIoOperationQueued();
            if (exchange.isDispatched()) {
                if (resumed) {
                    UndertowLogger.REQUEST_LOGGER.resumedAndDispatched();
                    exchange.setStatusCode(500);
                    exchange.endExchange();
                    return;
                }
                final Runnable dispatchTask = exchange.getDispatchTask();
                Executor executor = exchange.getDispatchExecutor();
                exchange.setDispatchExecutor(null);
                exchange.unDispatch();
                if (dispatchTask != null) {
                    executor = executor == null ? exchange.getConnection().getWorker() : executor;
                    try {
                        executor.execute(dispatchTask);
                    } catch (RejectedExecutionException e) {
                        UndertowLogger.REQUEST_LOGGER.debug("Failed to dispatch to worker", e);
                        exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
                        exchange.endExchange();
                    }
                }
            } else if (!resumed) {
                exchange.endExchange();
            } else {
                exchange.getConnection().runResumeReadWrite();
            }
        } catch (Throwable t) {
            connection.gatewayLog("executeRootHandler exception", t);
            exchange.putAttachment(DefaultResponseListener.EXCEPTION, t);
            exchange.getConnection().endExecutingHandlerChain(exchange);
            if (!exchange.isResponseStarted()) {
                exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            }
            if (t instanceof IOException) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException((IOException) t);
            } else {
                UndertowLogger.REQUEST_LOGGER.undertowRequestFailed(t, exchange);
            }
            exchange.endExchange();
        }
    }

    /**
     * Sets the request path and query parameters, decoding to the requested charset.
     *
     * @param exchange    The exchange
     * @param encodedPath The encoded path
     * @param charset     The charset
     */
    @Deprecated
    public static void setExchangeRequestPath(final HttpServerExchange exchange, final String encodedPath, final String charset, boolean decode, final boolean allowEncodedSlash, StringBuilder decodeBuffer) {
        try {
            setExchangeRequestPath(exchange, encodedPath, charset, decode, allowEncodedSlash, decodeBuffer, exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_PARAMETERS, UndertowOptions.DEFAULT_MAX_PARAMETERS));
        } catch (ParameterLimitException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the request path and query parameters, decoding to the requested charset.
     *
     * @param exchange    The exchange
     * @param encodedPath The encoded path
     * @param charset     The charset
     */
    public static void setExchangeRequestPath(final HttpServerExchange exchange, final String encodedPath, final String charset, boolean decode, final boolean allowEncodedSlash, StringBuilder decodeBuffer, int maxParameters) throws ParameterLimitException {
        boolean requiresDecode = false;
        for (int i = 0; i < encodedPath.length(); ++i) {
            char c = encodedPath.charAt(i);
            if (c == '?') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash, false, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                exchange.setRequestURI(encodedPart);
                final String qs = encodedPath.substring(i + 1);
                exchange.setQueryString(qs);
                URLUtils.parseQueryString(qs, exchange, charset, decode, maxParameters);
                return;
            } else if (c == ';') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash, false, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                for (int j = i; j < encodedPath.length(); ++j) {
                    if (encodedPath.charAt(j) == '?') {
                        exchange.setRequestURI(encodedPath.substring(0, j));
                        String pathParams = encodedPath.substring(i + 1, j);
                        URLUtils.parsePathParams(pathParams, exchange, charset, decode, maxParameters);
                        String qs = encodedPath.substring(j + 1);
                        exchange.setQueryString(qs);
                        URLUtils.parseQueryString(qs, exchange, charset, decode, maxParameters);
                        return;
                    }
                }
                exchange.setRequestURI(encodedPath);
                URLUtils.parsePathParams(encodedPath.substring(i + 1), exchange, charset, decode, maxParameters);
                return;
            } else if (c == '%' || c == '+') {
                requiresDecode = decode;
            }
        }

        String part;
        if (requiresDecode) {
            part = URLUtils.decode(encodedPath, charset, allowEncodedSlash, false, decodeBuffer);
        } else {
            part = encodedPath;
        }
        exchange.setRequestPath(part);
        exchange.setRelativePath(part);
        exchange.setRequestURI(encodedPath);
    }

    public static boolean isEntityBodyAllowed(HttpServerExchange exchange) {
        int code = exchange.getStatusCode();
        return isEntityBodyAllowed(code);
    }

    public static boolean isEntityBodyAllowed(int code) {
        if (code >= 100 && code < 200) {
            return false;
        }
        if (code == 204 || code == 304) {
            return false;
        }
        return true;
    }

    public static void updateResponseBytesSent(HttpServerExchange exchange, long bytes) {
        exchange.updateBytesSent(bytes);
    }

    /**
     * Verifies that the provided request headers are valid according to rfc7230. In particular:
     * - At most one content-length or transfer encoding
     */
    public static boolean areRequestHeadersValid(HeaderMap headers) {
        HeaderValues te = headers.get(HttpHeaderNames.TRANSFER_ENCODING);
        HeaderValues cl = headers.get(HttpHeaderNames.CONTENT_LENGTH);
        if (te != null && cl != null) {
            return false;
        } else if (te != null && te.size() > 1) {
            return false;
        } else if (cl != null && cl.size() > 1) {
            return false;
        }
        return true;
    }
}
