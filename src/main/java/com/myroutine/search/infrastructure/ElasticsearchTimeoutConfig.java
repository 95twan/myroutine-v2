package com.myroutine.search.infrastructure;

import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.elasticsearch.autoconfigure.Rest5ClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ElasticsearchTimeoutConfig {

    @Bean
    public Rest5ClientBuilderCustomizer rest5ClientBuilderCustomizer(@Value("${spring.elasticsearch.socket-timeout}") Duration timeout) {
        return new Rest5ClientBuilderCustomizer() {
            @Override
            public void customize(Rest5ClientBuilder builder) {

            }

            @Override
            public void customize(RequestConfig.Builder builder) {
                builder.setResponseTimeout(Timeout.of(timeout));
                builder.setConnectionRequestTimeout(Timeout.of(timeout));
            }
        };
    }
}
