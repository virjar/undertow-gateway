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

package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The request protocol
 *
 * @author Stuart Douglas
 */
public class RequestProtocolAttribute implements ExchangeAttribute {

    public static final String REQUEST_PROTOCOL_SHORT = "%H";
    public static final String REQUEST_PROTOCOL = "%{PROTOCOL}";

    public static final ExchangeAttribute INSTANCE = new RequestProtocolAttribute();

    private RequestProtocolAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.protocol();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Request protocol", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request protocol";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REQUEST_PROTOCOL) || token.equals(REQUEST_PROTOCOL_SHORT)) {
                return RequestProtocolAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
