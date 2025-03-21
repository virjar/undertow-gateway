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

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.predicate.PredicatesHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.JvmRouteHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.AccessControlListHandler;
import io.undertow.server.handlers.DateHandler;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.HttpContinueReadHandler;
import io.undertow.server.handlers.HttpTraceHandler;
import io.undertow.server.handlers.IPAddressAccessControlHandler;
import io.undertow.server.handlers.LearningPushHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.PathTemplateHandler;
import io.undertow.server.handlers.PredicateContextHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.ProxyPeerAddressHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.server.handlers.ResponseRateLimitingHandler;
import io.undertow.server.handlers.SetAttributeHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.URLDecodingHandler;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 * Utility class with convenience methods for dealing with handlers
 *
 * @author Stuart Douglas
 */
public class Handlers {

    /**
     * Creates a new path handler, with the default handler specified
     *
     * @param defaultHandler The default handler
     * @return A new path handler
     */
    public static PathHandler path(final HttpHandler defaultHandler) {
        return new PathHandler(defaultHandler);
    }

    /**
     * Creates a new path handler
     *
     * @return A new path handler
     */
    public static PathHandler path() {
        return new PathHandler();
    }

    /**
     * @return a new path template handler
     */
    public static PathTemplateHandler pathTemplate() {
        return new PathTemplateHandler();
    }

    /**
     * @param rewriteQueryParams If the query params should be rewritten
     * @return The routing handler
     */
    public static RoutingHandler routing(boolean rewriteQueryParams) {
        return new RoutingHandler(rewriteQueryParams);
    }

    /**
     * @return a new routing handler
     */
    public static RoutingHandler routing() {
        return new RoutingHandler();
    }

    /**
     * @param rewriteQueryParams If the query params should be rewritten
     * @return The path template handler
     */
    public static PathTemplateHandler pathTemplate(boolean rewriteQueryParams) {
        return new PathTemplateHandler(rewriteQueryParams);
    }


    /**
     * Creates a new virtual host handler
     *
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost() {
        return new NameVirtualHostHandler();
    }

    /**
     * Creates a new virtual host handler using the provided default handler
     *
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost(final HttpHandler defaultHandler) {
        return new NameVirtualHostHandler().setDefaultHandler(defaultHandler);
    }

    /**
     * Creates a new virtual host handler that uses the provided handler as the root handler for the given hostnames.
     *
     * @param hostHandler The host handler
     * @param hostnames   The host names
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost(final HttpHandler hostHandler, String... hostnames) {
        NameVirtualHostHandler handler = new NameVirtualHostHandler();
        for (String host : hostnames) {
            handler.addHost(host, hostHandler);
        }
        return handler;
    }

    /**
     * Creates a new virtual host handler that uses the provided handler as the root handler for the given hostnames.
     *
     * @param defaultHandler The default handler
     * @param hostHandler    The host handler
     * @param hostnames      The host names
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost(final HttpHandler defaultHandler, final HttpHandler hostHandler, String... hostnames) {
        return virtualHost(hostHandler, hostnames).setDefaultHandler(defaultHandler);
    }

    /**
     * Return a new resource handler
     *
     * @param resourceManager The resource manager to use
     * @return A new resource handler
     */
    public static ResourceHandler resource(final ResourceManager resourceManager) {
        return new ResourceHandler(resourceManager).setDirectoryListingEnabled(false);
    }

    /**
     * Returns a new redirect handler
     *
     * @param location The redirect location
     * @return A new redirect handler
     */
    public static RedirectHandler redirect(final String location) {
        return new RedirectHandler(location);
    }

    /**
     * Returns a new HTTP trace handler. This handler will handle HTTP TRACE
     * requests as per the RFC.
     * <p>
     * WARNING: enabling trace requests may leak information, in general it is recommended that
     * these be disabled for security reasons.
     *
     * @param next The next handler in the chain
     * @return A HTTP trace handler
     */
    public static HttpTraceHandler trace(final HttpHandler next) {
        return new HttpTraceHandler(next);
    }

    /**
     * Returns a new HTTP handler that sets the Date: header.
     * <p>
     * This is no longer necessary, as it is handled by the connectors directly.
     *
     * @param next The next handler in the chain
     * @return A new date handler
     */
    @Deprecated
    public static DateHandler date(final HttpHandler next) {
        return new DateHandler(next);
    }

    /**
     * Returns a new predicate handler, that will delegate to one of the two provided handlers based on the value of the
     * provided predicate.
     *
     * @param predicate    The predicate
     * @param trueHandler  The handler that will be executed if the predicate is true
     * @param falseHandler The handler that will be exected if the predicate is false
     * @return A new predicate handler
     * @see Predicate
     * @see io.undertow.predicate.Predicates
     */
    public static PredicateHandler predicate(final Predicate predicate, final HttpHandler trueHandler, final HttpHandler falseHandler) {
        return new PredicateHandler(predicate, trueHandler, falseHandler);
    }

    /**
     * @param next The next handler
     * @return a handler that sets up a new predicate context
     */
    public static HttpHandler predicateContext(HttpHandler next) {
        return new PredicateContextHandler(next);
    }

    public static PredicatesHandler predicates(final List<PredicatedHandler> handlers, HttpHandler next) {
        final PredicatesHandler predicatesHandler = new PredicatesHandler(next);
        for (PredicatedHandler handler : handlers) {
            predicatesHandler.addPredicatedHandler(handler);
        }
        return predicatesHandler;
    }

    /**
     * Returns a handler that sets a response header
     *
     * @param next        The next handler in the chain
     * @param headerName  The name of the header
     * @param headerValue The header value
     * @return A new set header handler
     */
    public static SetHeaderHandler header(final HttpHandler next, final String headerName, final String headerValue) {
        return new SetHeaderHandler(next, headerName, headerValue);
    }


    /**
     * Returns a handler that sets a response header
     *
     * @param next        The next handler in the chain
     * @param headerName  The name of the header
     * @param headerValue The header value
     * @return A new set header handler
     */
    public static SetHeaderHandler header(final HttpHandler next, final String headerName, final ExchangeAttribute headerValue) {
        return new SetHeaderHandler(next, headerName, headerValue);
    }


    /**
     * Returns a new handler that can allow or deny access to a resource based on IP address
     *
     * @param next         The next handler in the chain
     * @param defaultAllow Determine if a non-matching address will be allowed by default
     * @return A new IP access control handler
     */
    public static IPAddressAccessControlHandler ipAccessControl(final HttpHandler next, boolean defaultAllow) {
        return new IPAddressAccessControlHandler(next).setDefaultAllow(defaultAllow);
    }

    /**
     * Returns a new handler that can allow or deny access to a resource based an at attribute of the exchange
     *
     * @param next         The next handler in the chain
     * @param defaultAllow Determine if a non-matching user agent will be allowed by default
     * @return A new user agent access control handler
     */
    public static AccessControlListHandler acl(final HttpHandler next, boolean defaultAllow, ExchangeAttribute attribute) {
        return new AccessControlListHandler(next, attribute).setDefaultAllow(defaultAllow);
    }

    /**
     * A handler that automatically handles HTTP 100-continue responses, by sending a continue
     * response when the first attempt is made to read from the request channel.
     *
     * @param next The next handler in the chain
     * @return A new continue handler
     */
    public static HttpContinueReadHandler httpContinueRead(final HttpHandler next) {
        return new HttpContinueReadHandler(next);
    }

    /**
     * A handler that will decode the URL, query parameters and to the specified charset.
     * <p>
     * If you are using this handler you must set the {@link UndertowOptions#DECODE_URL} parameter to false.
     * <p>
     * This is not as efficient as using the parsers built in UTF-8 decoder. Unless you need to decode to something other
     * than UTF-8 you should rely on the parsers decoding instead.
     *
     * @param next    The next handler in the chain
     * @param charset The charset to decode to
     * @return a new url decoding handler
     */
    public static URLDecodingHandler urlDecoding(final HttpHandler next, final String charset) {
        return new URLDecodingHandler(next, charset);
    }

    /**
     * Returns an attribute setting handler that can be used to set an arbitrary attribute on the exchange.
     * This includes functions such as adding and removing headers etc.
     *
     * @param next        The next handler
     * @param attribute   The attribute to set, specified as a string presentation of an {@link io.undertow.attribute.ExchangeAttribute}
     * @param value       The value to set, specified an a string representation of an {@link io.undertow.attribute.ExchangeAttribute}
     * @param classLoader The class loader to use to parser the exchange attributes
     * @return The handler
     */
    public static SetAttributeHandler setAttribute(final HttpHandler next, final String attribute, final String value, final ClassLoader classLoader) {
        return new SetAttributeHandler(next, attribute, value, classLoader);
    }

    /**
     * Creates the set of handlers that are required to perform a simple rewrite.
     *
     * @param condition The rewrite condition
     * @param target    The rewrite target if the condition matches
     * @param next      The next handler
     * @return
     */
    public static HttpHandler rewrite(final String condition, final String target, final ClassLoader classLoader, final HttpHandler next) {
        return predicateContext(predicate(PredicateParser.parse(condition, classLoader), setAttribute(next, "%R", target, classLoader), next));
    }

    /**
     * Returns a new handler that decodes the URL and query parameters into the specified charset, assuming it
     * has not already been done by the connector. For this handler to take effect the parameter
     * {@link UndertowOptions#DECODE_URL} must have been set to false.
     *
     * @param charset The charset to decode
     * @param next    The next handler
     * @return A handler that decodes the URL
     */
    public static HttpHandler urlDecodingHandler(final String charset, final HttpHandler next) {
        return new URLDecodingHandler(next, charset);
    }


    /**
     * Returns a new handler that can be used to wait for all requests to finish before shutting down the server gracefully.
     *
     * @param next The next http handler
     * @return The graceful shutdown handler
     */
    public static GracefulShutdownHandler gracefulShutdown(HttpHandler next) {
        return new GracefulShutdownHandler(next);
    }

    /**
     * Returns a new handler that sets the peer address based on the X-Forwarded-For and
     * X-Forwarded-Proto header
     *
     * @param next The next http handler
     * @return The handler
     */
    public static ProxyPeerAddressHandler proxyPeerAddress(HttpHandler next) {
        return new ProxyPeerAddressHandler(next);
    }

    /**
     * Handler that appends the JVM route to the session cookie
     *
     * @param sessionCookieName The session cookie name
     * @param jvmRoute          The JVM route to append
     * @param next              The next handler
     * @return The handler
     */
    public static JvmRouteHandler jvmRoute(final String sessionCookieName, final String jvmRoute, HttpHandler next) {
        return new JvmRouteHandler(next, sessionCookieName, jvmRoute);
    }

    /**
     * Returns a handler that limits the maximum number of requests that can run at a time.
     *
     * @param maxRequest The maximum number of requests
     * @param queueSize  The maximum number of queued requests
     * @param next       The next handler
     * @return The handler
     */
    public static RequestLimitingHandler requestLimitingHandler(final int maxRequest, final int queueSize, HttpHandler next) {
        return new RequestLimitingHandler(maxRequest, queueSize, next);
    }

    /**
     * Returns a handler that limits the maximum number of requests that can run at a time.
     *
     * @param requestLimit The request limit object that can be shared between handlers, to apply the same limits across multiple handlers
     * @param next         The next handler
     * @return The handler
     */
    public static RequestLimitingHandler requestLimitingHandler(final RequestLimit requestLimit, HttpHandler next) {
        return new RequestLimitingHandler(requestLimit, next);
    }

    /**
     * Handler that sets the headers that disable caching of the response
     *
     * @param next The next handler
     * @return The handler
     */
    public static HttpHandler disableCache(final HttpHandler next) {
        return new DisableCacheHandler(next);
    }

    /**
     * Returns a handler that dumps requests to the log for debugging purposes.
     *
     * @param next The next handler
     * @return The request dumping handler
     */
    public static HttpHandler requestDump(final HttpHandler next) {
        return new RequestDumpingHandler(next);
    }

    /**
     * Returns a handler that maps exceptions to additional handlers
     *
     * @param next The next handler
     * @return The exception handler
     */
    public static ExceptionHandler exceptionHandler(final HttpHandler next) {
        return new ExceptionHandler(next);
    }

    /**
     * A handler that limits the download speed to a set number of bytes/period
     *
     * @param next     The next handler
     * @param bytes    The number of bytes per time period
     * @param time     The time period
     * @param timeUnit The units of the time period
     */
    public static ResponseRateLimitingHandler responseRateLimitingHandler(HttpHandler next, int bytes, long time, TimeUnit timeUnit) {
        return new ResponseRateLimitingHandler(next, bytes, time, timeUnit);
    }

    /**
     * Creates a handler that automatically learns which resources to push based on the referer header
     *
     * @param maxEntries The maximum number of entries to store
     * @param maxAge     The maximum age of the entries
     * @param next       The next handler
     * @return A caching push handler
     */
    public static LearningPushHandler learningPushHandler(int maxEntries, int maxAge, HttpHandler next) {
        return new LearningPushHandler(maxEntries, maxAge, next);
    }

    /**
     * Creates a handler that automatically learns which resources to push based on the referer header
     *
     * @param maxEntries The maximum number of entries to store
     * @param next       The next handler
     * @return A caching push handler
     */
    public static LearningPushHandler learningPushHandler(int maxEntries, HttpHandler next) {
        return new LearningPushHandler(maxEntries, -1, next);
    }

    private Handlers() {

    }

    public static void handlerNotNull(final HttpHandler handler) {
        if (handler == null) {
            throw UndertowMessages.MESSAGES.handlerCannotBeNull();
        }
    }
}
