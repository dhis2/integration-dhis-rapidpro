package org.hisp.dhis.integration.rapidpro.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty( value = "webhook.auth", havingValue = "none" )
public class WebhookDefaultSecurityConfig
{
    @Bean
    protected SecurityFilterChain filterChain( HttpSecurity http )
        throws Exception
    {
        return http.antMatcher( "/rapidProConnector/webhook" ).csrf()
            .disable().build();
    }
}
