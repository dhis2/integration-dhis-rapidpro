package org.hisp.dhis.integration.rapidpro;

import org.hisp.dhis.integration.sdk.Dhis2Client;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application
{
    @Value( "${dhis2.api.url}" )
    private String baseApiUrl;

    @Value( "${dhis2.api.username}" )
    private String username;

    @Value( "${dhis2.api.password}" )
    private String password;

    public static void main( String[] args )
    {
        SpringApplication springApplication = new SpringApplication( Application.class );
        springApplication.setBannerMode( Banner.Mode.OFF );
        springApplication.run( args );
    }

    @Bean
    public Dhis2Client dhis2Client()
    {
        return Dhis2ClientBuilder.newClient( baseApiUrl, username, password ).build();
    }
}
