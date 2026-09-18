package com.carry.user.adapter.outbound.auth

import com.carry.user.application.port.outbound.OAuthProfileClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class NaverClientConfig {

    @Bean
    fun naverOAuthClient(properties: NaverProperties): OAuthProfileClient =
        NaverOAuthClient(RestClient.builder().baseUrl(properties.baseUrl).build())
}
