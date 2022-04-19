package org.hisp.dhis2.integration.rapidpro;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application
{
    public static void main( String[] args )
    {
        SpringApplication springApplication = new SpringApplication( Application.class );
        springApplication.setBannerMode( Banner.Mode.OFF );
        springApplication.run( args );
    }
}
