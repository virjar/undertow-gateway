/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

import org.xnio.Option;

public class UndertowOption<T> extends Option<T> {

    private final String name;
    private final Class<T> type;
    private final transient ValueParser<T> parser;

    UndertowOption(String name, Class<T> type) {
        this.name = name;
        this.type = type;
        parser = Option.getParser(type);
    }

    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    public static <T> UndertowOption<T> create(String name, Class<T> type) {
        return new UndertowOption<>(name, type);
    }

    public T parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
        return parser.parseValue(string, classLoader);
    }
}
