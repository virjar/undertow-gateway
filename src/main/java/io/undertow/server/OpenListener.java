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


import io.netty.buffer.ByteBufAllocator;
import io.undertow.util.UndertowOptionMap;

/**
 * Interface that represents an open listener, aka a connector.
 *
 * @author Stuart Douglas
 */
public interface OpenListener  {

    /**
     *
     * @return The first handler that will be executed by requests on the connector
     */
    HttpHandler getRootHandler();

    /**
     * Sets the root handler
     *
     * @param rootHandler The new root handler
     */
    void setRootHandler(HttpHandler rootHandler);

    /**
     *
     * @return The connector options
     */
    UndertowOptionMap getUndertowOptions();

    /**
     *
     * @param undertowOptions The connector options
     */
    void setUndertowOptions(UndertowOptionMap undertowOptions);

    /**
     *
     * @return The buffer pool in use by this connector
     */
    ByteBufAllocator getBufferPool();

    /**
     *
     * @return The connector statistics, or null if statistics gathering is disabled.
     */
    ConnectorStatistics getConnectorStatistics();

    /**
     * Close all active connections that were handled by this listener
     */
    default void closeConnections() {
        //nnop by default
    }
}
