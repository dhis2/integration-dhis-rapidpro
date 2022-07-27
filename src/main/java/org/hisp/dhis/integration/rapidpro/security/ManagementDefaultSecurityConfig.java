package org.hisp.dhis.integration.rapidpro.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class ManagementDefaultSecurityConfig
{
    @Bean
    @ConditionalOnProperty( value = "management.auth", havingValue = "basic", matchIfMissing = true )
    protected SecurityFilterChain managementFilterChain( HttpSecurity http )
        throws Exception
    {
        return http.antMatcher( "/management/**" ).antMatcher( "/rapidProConnector/sync" ).authorizeRequests()
            .anyRequest().authenticated()
            .and().csrf().ignoringAntMatchers( "/management/h2-console/**" )
            .and()
            .formLogin()
            .and()
            .httpBasic()
            .and()
            .headers().frameOptions().sameOrigin().and()
            .csrf().csrfTokenRepository( CookieCsrfTokenRepository.withHttpOnlyFalse() ).and().build();
    }
}
