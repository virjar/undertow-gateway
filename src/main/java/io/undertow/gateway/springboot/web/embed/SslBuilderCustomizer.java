/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.gateway.springboot.web.embed;

import io.undertow.Undertow;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.web.server.Ssl.ClientAuth;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;

/**
 * {@link UndertowBuilderCustomizer} that configures SSL on the given builder instance.
 *
 * @author Brian Clozel
 * @author Raheela Aslam
 * @author Cyril Dangerville
 * @author Scott Frederick
 */
class SslBuilderCustomizer implements UndertowBuilderCustomizer {

	private final int port;

	private final InetAddress address;

	private final ClientAuth clientAuth;

	private final SslBundle sslBundle;

	SslBuilderCustomizer(int port, InetAddress address, ClientAuth clientAuth, SslBundle sslBundle) {
		this.port = port;
		this.address = address;
		this.clientAuth = clientAuth;
		this.sslBundle = sslBundle;
	}

	@Override
	public void customize(Undertow.Builder builder) {
		SslOptions options = this.sslBundle.getOptions();
		SSLContext sslContext = this.sslBundle.createSslContext();
		builder.addHttpsListener(this.port, getListenAddress(), sslContext);
//		builder.setSocketOption(UndertowOptions.SSL_CLIENT_AUTH_MODE, ClientAuth.map(this.clientAuth,
//				SslClientAuthMode.NOT_REQUESTED, SslClientAuthMode.REQUESTED, SslClientAuthMode.REQUIRED));
//		if (options.getEnabledProtocols() != null) {
//			builder.setSocketOption(UndertowOptions.SSL_ENABLED_PROTOCOLS, Sequence.of(options.getEnabledProtocols()));
//		}
//		if (options.getCiphers() != null) {
//			builder.setSocketOption(UndertowOptions.SSL_ENABLED_CIPHER_SUITES, Sequence.of(options.getCiphers()));
//		}
	}

	private String getListenAddress() {
		if (this.address == null) {
			return "0.0.0.0";
		}
		return this.address.getHostAddress();
	}

}
