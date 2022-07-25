package org.hisp.dhis.integration.rapidpro;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig
{
    @Bean
    protected SecurityFilterChain filterChain( HttpSecurity http )
        throws Exception
    {
        return http.authorizeRequests().antMatchers( "/management/**", "/rapidProConnector/sync" ).authenticated()
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
