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

package io.undertow.server.handlers.form;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import io.netty.handler.codec.http.HttpHeaders;
import io.undertow.UndertowMessages;

/**
 * Representation of form data.
 * <p>
 * TODO: add representation of multipart data
 */
public final class FormData implements Iterable<String> {

    private final Map<String, Deque<FormValue>> values = new LinkedHashMap<>();

    private final int maxValues;
    private int valueCount = 0;

    public FormData(final int maxValues) {
        this.maxValues = maxValues;
    }


    public Iterator<String> iterator() {
        return values.keySet().iterator();
    }

    public FormValue getFirst(String name) {
        final Deque<FormValue> deque = values.get(name);
        return deque == null ? null : deque.peekFirst();
    }

    public FormValue getLast(String name) {
        final Deque<FormValue> deque = values.get(name);
        return deque == null ? null : deque.peekLast();
    }

    public Deque<FormValue> get(String name) {
        return values.get(name);
    }

    public void add(String name, byte[] value, String fileName, HttpHeaders headers) {
        Deque<FormValue> values = this.values.get(name);
        if (values == null) {
            this.values.put(name, values = new ArrayDeque<>(1));
        }
        values.add(new FormValueImpl(value, fileName, headers));
        if (++valueCount > maxValues) {
            throw new RuntimeException(UndertowMessages.MESSAGES.tooManyParameters(maxValues));
        }
    }

    public void add(String name, String value) {
        add(name, value, null);
    }

    public void add(String name, String value, final HttpHeaders headers) {
        Deque<FormValue> values = this.values.get(name);
        if (values == null) {
            this.values.put(name, values = new ArrayDeque<>(1));
        }
        values.add(new FormValueImpl(value, headers));
        if (++valueCount > maxValues) {
            throw new RuntimeException(UndertowMessages.MESSAGES.tooManyParameters(maxValues));
        }
    }

    public void add(String name, Path value, String fileName, final HttpHeaders headers) {
        Deque<FormValue> values = this.values.get(name);
        if (values == null) {
            this.values.put(name, values = new ArrayDeque<>(1));
        }
        values.add(new FormValueImpl(value, fileName, headers));
        if (values.size() > maxValues) {
            throw new RuntimeException(UndertowMessages.MESSAGES.tooManyParameters(maxValues));
        }
        if (++valueCount > maxValues) {
            throw new RuntimeException(UndertowMessages.MESSAGES.tooManyParameters(maxValues));
        }
    }

    public void put(String name, String value, final HttpHeaders headers) {
        Deque<FormValue> values = new ArrayDeque<>(1);
        Deque<FormValue> old = this.values.put(name, values);
        if (old != null) {
            valueCount -= old.size();
        }
        values.add(new FormValueImpl(value, headers));

        if (++valueCount > maxValues) {
            throw new RuntimeException(UndertowMessages.MESSAGES.tooManyParameters(maxValues));
        }
    }

    public Deque<FormValue> remove(String name) {
        Deque<FormValue> old = values.remove(name);
        if (old != null) {
            valueCount -= old.size();
        }
        return old;
    }

    public boolean contains(String name) {
        final Deque<FormValue> value = values.get(name);
        return value != null && !value.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final FormData strings = (FormData) o;

        if (values != null ? !values.equals(strings.values) : strings.values != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return values != null ? values.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "FormData{" +
                "values=" + values +
                '}';
    }


    public interface FormValue {

        /**
         * @return the simple string value.
         * @throws IllegalStateException If this is not a simple string value
         */
        String getValue();

        /**
         * Returns true if this is a file and not a simple string
         *
         * @return
         */
        @Deprecated
        boolean isFile();

        /**
         * @return The temp file that the file data was saved to
         * @throws IllegalStateException if this is not a file
         */
        @Deprecated
        Path getPath();

        @Deprecated
        File getFile();

        FileItem getFileItem();

        boolean isFileItem();

        /**
         * @return The filename specified in the disposition header.
         */
        String getFileName();

        /**
         * @return The headers that were present in the multipart request, or null if this was not a multipart request
         */
        HttpHeaders getHeaders();


    }

    public static class FileItem {
        private final Path file;
        private final byte[] content;

        public FileItem(Path file) {
            this.file = file;
            this.content = null;
        }

        public FileItem(byte[] content) {
            this.file = null;
            this.content = content;
        }

        public boolean isInMemory() {
            return file == null;
        }

        public Path getFile() {
            return file;
        }

        public long getFileSize() throws IOException {
            if (isInMemory()) {
                return content.length;
            } else {
                return Files.size(file);
            }
        }

        public InputStream getInputStream() throws IOException {
            if (file != null) {
                return new BufferedInputStream(Files.newInputStream(file));
            } else {
                return new ByteArrayInputStream(content);
            }
        }

        public void delete() throws IOException {
            if (file != null) {
                try {
                    Files.delete(file);
                } catch (NoSuchFileException e) { //already deleted
                }
            }
        }

        public void write(Path target) throws IOException {
            if (file != null) {
                try {
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.copy(getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.copy(getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }


    static class FormValueImpl implements FormValue {

        private final String value;
        private final String fileName;
        private final HttpHeaders headers;
        private final FileItem fileItem;

        FormValueImpl(String value, HttpHeaders headers) {
            this.value = value;
            this.headers = headers;
            this.fileName = null;
            this.fileItem = null;
        }

        FormValueImpl(Path file, final String fileName, HttpHeaders headers) {
            this.fileItem = new FileItem(file);
            this.headers = headers;
            this.fileName = fileName;
            this.value = null;
        }

        FormValueImpl(byte[] data, String fileName, HttpHeaders headers) {
            this.fileItem = new FileItem(data);
            this.fileName = fileName;
            this.headers = headers;
            this.value = null;
        }


        @Override
        public String getValue() {
            if (value == null) {
                throw UndertowMessages.MESSAGES.formValueIsAFile();
            }
            return value;
        }

        @Override
        public boolean isFile() {
            return fileItem != null && !fileItem.isInMemory();
        }

        @Override
        public Path getPath() {
            if (fileItem == null) {
                throw UndertowMessages.MESSAGES.formValueIsAString();
            }
            if (fileItem.isInMemory()) {
                throw UndertowMessages.MESSAGES.formValueIsInMemoryFile();
            }
            return fileItem.getFile();
        }

        @Override
        public File getFile() {
            return getPath().toFile();
        }

        @Override
        public FileItem getFileItem() {
            if (fileItem == null) {
                throw UndertowMessages.MESSAGES.formValueIsAString();
            }
            return fileItem;
        }

        @Override
        public boolean isFileItem() {
            return fileItem != null;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
