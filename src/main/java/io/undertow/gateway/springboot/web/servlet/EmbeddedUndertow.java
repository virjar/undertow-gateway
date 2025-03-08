package io.undertow.gateway.springboot.web.servlet;

import io.undertow.Undertow;
import io.undertow.gateway.GatewayHandler;
import io.undertow.gateway.springboot.web.embed.UndertowBuilderCustomizer;
import io.undertow.gateway.springboot.web.embed.UndertowDeploymentInfoCustomizer;
import io.undertow.gateway.springboot.web.embed.UndertowServletWebServerFactory;
import jakarta.servlet.Servlet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nested configuration if Undertow is being used.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Servlet.class, Undertow.class, GatewayHandler.class})
@ConditionalOnMissingBean(value = ServletWebServerFactory.class, search = SearchStrategy.CURRENT)
public class EmbeddedUndertow {

    @Bean
    UndertowServletWebServerFactory undertowServletWebServerFactory(
            ObjectProvider<UndertowDeploymentInfoCustomizer> deploymentInfoCustomizers,
            ObjectProvider<UndertowBuilderCustomizer> builderCustomizers) {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        factory.getDeploymentInfoCustomizers().addAll(deploymentInfoCustomizers.orderedStream().toList());
        factory.getBuilderCustomizers().addAll(builderCustomizers.orderedStream().toList());
        return factory;
    }

    @Bean
    UndertowServletWebServerFactoryCustomizer undertowServletWebServerFactoryCustomizer(
            ServerProperties serverProperties) {
        return new UndertowServletWebServerFactoryCustomizer(serverProperties);
    }

}