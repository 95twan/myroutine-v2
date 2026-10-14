package com.myroutine.payment.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossClientConfig {

    @Bean
    public RestClient tossRestClient(TossProperties p) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(p.connectTimeout())
                        .build());
        factory.setReadTimeout(p.readTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(p.baseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString((p.secretKey() + ":").getBytes(StandardCharsets.UTF_8))
                )
                .build();
    }
}
