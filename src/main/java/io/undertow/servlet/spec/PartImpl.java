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

package io.undertow.servlet.spec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.Part;

import io.undertow.server.handlers.form.FormData;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.HttpHeaderNames;

/**
 * @author Stuart Douglas
 */
public class PartImpl implements Part {

    private final String name;
    private final FormData.FormValue formValue;
    private final MultipartConfigElement config;
    private final ServletContextImpl servletContext;
    private final HttpServletRequestImpl servletRequest;

    public PartImpl(final String name, final FormData.FormValue formValue, MultipartConfigElement config,
                    ServletContextImpl servletContext, HttpServletRequestImpl servletRequest) {
        this.name = name;
        this.formValue = formValue;
        this.config = config;
        this.servletContext = servletContext;
        this.servletRequest = servletRequest;

    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (formValue.isFileItem()) {
            return formValue.getFileItem().getInputStream();
        } else {
            String requestedCharset = servletRequest.getCharacterEncoding();
            String charset = requestedCharset != null ? requestedCharset : servletContext.getDeployment().getDefaultRequestCharset().name();
            return new ByteArrayInputStream(formValue.getValue().getBytes(charset));
        }
    }

    @Override
    public String getContentType() {
        return formValue.getHeaders().get(HttpHeaderNames.CONTENT_TYPE);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSubmittedFileName() {
        return formValue.getFileName();
    }

    @Override
    public long getSize() {
        try {
            if (formValue.isFileItem()) {
                return formValue.getFileItem().getFileSize();
            } else {
                return formValue.getValue().length();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(final String fileName) throws IOException {
        Path target = Paths.get(fileName);
        if(!target.isAbsolute()) {
            if(config.getLocation().isEmpty()) {
                target = servletContext.getDeployment().getDeploymentInfo().getTempPath().resolve(fileName);
            } else {
                target = Paths.get(config.getLocation(), fileName);
            }
        }
        if (formValue.isFileItem()) {
            formValue.getFileItem().write(target);
        }
    }

    @Override
    public void delete() throws IOException {
        if (formValue.isFileItem()) {
            try {
                formValue.getFileItem().delete();
            } catch (IOException e) {
                throw UndertowServletMessages.MESSAGES.deleteFailed(formValue.getPath());
            }
        }
    }

    @Override
    public String getHeader(final String name) {
        return formValue.getHeaders().get(name);
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        List<String> values = formValue.getHeaders().getAll(name);
        return values == null ? Collections.<String>emptyList() : values;
    }

    @Override
    public Collection<String> getHeaderNames() {
        final Set<String> ret = new HashSet<>();
        for (Map.Entry<String, String> i : formValue.getHeaders()) {
            ret.add(i.getKey());
        }
        return ret;
    }
}
