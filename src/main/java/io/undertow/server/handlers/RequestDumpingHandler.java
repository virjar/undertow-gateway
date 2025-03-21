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

import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.undertow.UndertowLogger;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.LocaleUtils;

/**
 * Handler that dumps a exchange to a log.
 *
 * @author Stuart Douglas
 */
public class RequestDumpingHandler implements HttpHandler {

    private final HttpHandler next;

    public RequestDumpingHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final StringBuilder sb = new StringBuilder();
// Log pre-service information
        final SecurityContext sc = exchange.getSecurityContext();
        sb.append("\n----------------------------REQUEST---------------------------\n");
        sb.append("               URI=" + exchange.getRequestURI() + "\n");
        sb.append(" characterEncoding=" + exchange.requestHeaders().get(HttpHeaderNames.CONTENT_ENCODING) + "\n");
        sb.append("     contentLength=" + exchange.getRequestContentLength() + "\n");
        sb.append("       contentType=" + exchange.requestHeaders().get(HttpHeaderNames.CONTENT_TYPE) + "\n");
        //sb.append("       contextPath=" + exchange.getContextPath());
        if (sc != null) {
            if (sc.isAuthenticated()) {
                sb.append("          authType=" + sc.getMechanismName() + "\n");
                sb.append("         principle=" + sc.getAuthenticatedAccount().getPrincipal() + "\n");
            } else {
                sb.append("          authType=none" + "\n");
            }
        }

        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
                Cookie cookie = entry.getValue();
                sb.append("            cookie=" + cookie.getName() + "=" +
                        cookie.getValue() + "\n");
            }
        }
        for (Map.Entry<String, String> header : exchange.requestHeaders()) {
            for (String value : exchange.requestHeaders().getAll(header.getKey())) {
                sb.append("            header=" + header.getKey() + "=" + value + "\n");
            }
        }
        sb.append("            locale=" + LocaleUtils.getLocalesFromHeader(exchange.requestHeaders().get(HttpHeaderNames.ACCEPT_LANGUAGE)) + "\n");
        sb.append("            method=" + exchange.requestMethod() + "\n");
        Map<String, Deque<String>> pnames = exchange.getQueryParameters();
        for (Map.Entry<String, Deque<String>> entry : pnames.entrySet()) {
            String pname = entry.getKey();
            Iterator<String> pvalues = entry.getValue().iterator();
            sb.append("         parameter=");
            sb.append(pname);
            sb.append('=');
            while (pvalues.hasNext()) {
                sb.append(pvalues.next());
                if (pvalues.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("\n");
        }
        //sb.append("          pathInfo=" + exchange.getPathInfo());
        sb.append("          protocol=" + exchange.protocol() + "\n");
        sb.append("       queryString=" + exchange.getQueryString() + "\n");
        sb.append("        remoteAddr=" + exchange.getSourceAddress() + "\n");
        sb.append("        remoteHost=" + exchange.getSourceAddress().getHostName() + "\n");
        //sb.append("requestedSessionId=" + exchange.getRequestedSessionId());
        sb.append("            scheme=" + exchange.getRequestScheme() + "\n");
        sb.append("              host=" + exchange.requestHeaders().get(HttpHeaderNames.HOST) + "\n");
        sb.append("        serverPort=" + exchange.getDestinationAddress().getPort() + "\n");
        //sb.append("       servletPath=" + exchange.getServletPath());
        sb.append("          isSecure=" + exchange.isSecure() + "\n");

        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {

                dumpRequestBody(exchange, sb);

                // Log post-service information
                sb.append("--------------------------RESPONSE--------------------------\n");
                if (sc != null) {
                    if (sc.isAuthenticated()) {
                        sb.append("          authType=" + sc.getMechanismName() + "\n");
                        sb.append("         principle=" + sc.getAuthenticatedAccount().getPrincipal() + "\n");
                    } else {
                        sb.append("          authType=none" + "\n");
                    }
                }
                sb.append("     contentLength=" + exchange.getResponseContentLength() + "\n");
                sb.append("       contentType=" + exchange.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE) + "\n");
                Map<String, Cookie> cookies = exchange.getResponseCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies.values()) {
                        sb.append("            cookie=" + cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain() + "; path=" + cookie.getPath() + "\n");
                    }
                }
                for (Map.Entry<String, String> header : exchange.responseHeaders()) {
                    for (String value : exchange.responseHeaders().getAll(header.getKey())) {
                        sb.append("            header=" + header.getKey() + "=" + value + "\n");
                    }
                }
                sb.append("            status=" + exchange.getStatusCode() + "\n");
//                String storedResponse = StoredResponse.INSTANCE.readAttribute(exchange);
//                if (storedResponse != null) {
//                    sb.append("body=\n");
//                    sb.append(storedResponse);
//                }

                sb.append("\n==============================================================");


                nextListener.proceed();
                UndertowLogger.REQUEST_DUMPER_LOGGER.info(sb.toString());
            }
        });


        // Perform the exchange
        next.handleRequest(exchange);
    }

    private void dumpRequestBody(HttpServerExchange exchange, StringBuilder sb) {
//        try {
//            FormData formData = exchange.getAttachment(FormDataParser.FORM_DATA);
//            if (formData != null) {
//                sb.append("body=\n");
//
//                for (String formField : formData) {
//                    Deque<FormData.FormValue> formValues = formData.get(formField);
//
//                    sb.append(formField)
//                            .append("=");
//                    for (FormData.FormValue formValue : formValues) {
//                        sb.append(formValue.isFileItem() ? "[file-content]" : formValue.getValue());
//                        sb.append("\n");
//
//                        if (formValue.getHeaders() != null) {
//                            sb.append("headers=\n");
//                            for (HeaderValues header : formValue.getHeaders()) {
//                                sb.append("\t")
//                                        .append(header.getHeaderName()).append("=").append(header.get()).append("\n");
//
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "dump-request";
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
            return new RequestDumpingHandler(handler);
        }
    }
}
