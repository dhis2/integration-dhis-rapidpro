/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.rapidpro;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.camel.CamelContext;
import org.apache.commons.io.FileUtils;
import org.hisp.dhis.integration.rapidpro.security.KeyStoreGenerator;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.Dhis2ClientException;
import org.hisp.dhis.integration.sdk.api.Dhis2Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@PropertySource( "${sql.data-location}" )
public class Application extends SpringBootServletInitializer
{
    protected static final Logger LOGGER = LoggerFactory.getLogger( Application.class );

    @Value( "${dhis2.api.url:}" )
    private String dhis2ApiUrl;

    @Value( "${dhis2.api.username:#{null}}" )
    private String username;

    @Value( "${dhis2.api.password:#{null}}" )
    private String password;

    @Value( "${dhis2.api.pat:#{null}}" )
    private String pat;

    @Value( "${rapidpro.api.url:#{null}}" )
    private String rapidProApiUrl;

    @Value( "${rapidpro.api.token:#{null}}" )
    private String rapidProApiToken;

    @Value( "${test.connection.startup:true}" )
    private Boolean testConnectionOnStartUp;

    @Value( "${server.ssl.enabled}" )
    private Boolean serverSslEnabled;

    @Value( "${camel.springboot.routes-reload-directory}" )
    private String routesReloadDirectory;

    @Value( "${server.port}" )
    private int serverPort;

    @Value( "${server.servlet.context-path}" )
    private String serverServletContextPath;

    @Value( "${management.endpoints.web.base-path}" )
    private String managementEndpointsWebBasePath;

    @Value( "${spring.h2.console.path}" )
    private String h2ConsolePath;

    @Value( "${rapidpro.webhook.enabled}" )
    private Boolean rapidProWebhookEnabled;

    @Autowired
    private ArtemisProperties artemisProperties;

    @Autowired
    private KeyStoreGenerator keyStoreGenerator;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private NativeDataSonnetLibrary nativeDataSonnetLibrary;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private ApplicationArguments applicationArguments;

    @Autowired
    private DataSource dataSource;
    
    @PostConstruct
    public void postConstruct()
        throws
        IOException
    {
        String sensitivePropertyErrorMessage = "`%s` is not permitted in a command-line argument. Hint: set it in an environment variable or inside the application properties file";

        if ( !isNullOrEmpty( applicationArguments.getOptionValues( "dhis2.api.pat" ) ) )
        {
            terminate( String.format( sensitivePropertyErrorMessage, "dhis2.api.pat" ) );
        }

        if ( !isNullOrEmpty( applicationArguments.getOptionValues( "dhis2.api.password" ) ) )
        {
            terminate( String.format( sensitivePropertyErrorMessage, "dhis2.api.password" ) );
        }

        if ( !isNullOrEmpty( applicationArguments.getOptionValues( "rapidpro.api.token" ) ) )
        {
            terminate( String.format( sensitivePropertyErrorMessage, "rapidpro.api.token" ) );
        }

        if ( !isNullOrEmpty( applicationArguments.getOptionValues( "spring.security.user.password" ) ) )
        {
            terminate( String.format( sensitivePropertyErrorMessage, "spring.security.user.password" ) );
        }

        if ( !isNullOrEmpty( applicationArguments.getOptionValues( "spring.datasource.password" ) ) )
        {
            terminate( String.format( sensitivePropertyErrorMessage, "spring.datasource.password" ) );
        }

        if ( !StringUtils.hasText( rapidProApiUrl ) )
        {
            terminate( "Missing RapidPro API URL. Are you sure that you set `rapidpro.api.url`?" );
        }

        if ( !StringUtils.hasText( rapidProApiToken ) )
        {
            terminate( "Missing RapidPro API token. Are you sure that you set `rapidpro.api.token`?" );
        }

        if ( testConnectionOnStartUp )
        {
            testRapidProConnection();
        }
        FileUtils.forceMkdir( new File( routesReloadDirectory ) );
        if ( serverSslEnabled )
        {
            keyStoreGenerator.generate();
        }
        camelContext.getRegistry().bind( "native", nativeDataSonnetLibrary );
    }

    public static void main( String[] args )
        throws
        SQLException
    {
        SpringApplication springApplication = new SpringApplication( Application.class );
        springApplication.run( args );
    }

    @EventListener( ApplicationReadyEvent.class )
    public void onApplicationReadyEvent()
        throws
        IOException
    {

        String baseUrl = String.format( "%s://%s:%s%s", serverSslEnabled ? "https" : "http",
            InetAddress.getLocalHost().getHostAddress(), serverPort,
            serverServletContextPath.startsWith( "/" ) ? serverServletContextPath : "/" + serverServletContextPath );

        StringBuilder onlineBanner = new StringBuilder();

        onlineBanner.append( "Hawtio console: " ).append( baseUrl ).append( managementEndpointsWebBasePath )
            .append( "/hawtio\n" );
        onlineBanner.append( " H2 console: " ).append( baseUrl ).append( h2ConsolePath ).append( "\n" );
        if ( rapidProWebhookEnabled )
        {
            onlineBanner.append( " RapidPro webhook: " ).append( baseUrl ).append( "/services/webhook\n" );
        }
        onlineBanner.append( " Poll flows task: " ).append( baseUrl ).append( "/services/tasks/scan\n" );
        onlineBanner.append( " Sync contacts task: " ).append( baseUrl ).append( "/services/tasks/sync\n" );
        onlineBanner.append( " Remind contacts task: " ).append( baseUrl ).append( "/services/tasks/reminders\n" );

        LOGGER.info(
            String.format( StreamUtils.copyToString(
                    Thread.currentThread().getContextClassLoader().getResourceAsStream( "online-banner.txt" ),
                    StandardCharsets.UTF_8 ),
                onlineBanner ) );
    }

    @Bean
    public Dhis2Client dhis2Client()
        throws
        Dhis2RapidProException
    {
        if ( !StringUtils.hasText( dhis2ApiUrl ) )
        {
            terminate( "Missing DHIS2 API URL. Are you sure that you set `dhis2.api.url`?" );
        }

        if ( pat != null && (username != null || password != null) )
        {
            terminate(
                "Bad DHIS2 authentication configuration: PAT authentication and basic authentication are mutually exclusive. Either set `dhis2.api.pat` or both `dhis2.api.username` and `dhis2.api.password`" );
        }

        Dhis2Client dhis2Client = null;
        if ( StringUtils.hasText( pat ) )
        {
            dhis2Client = Dhis2ClientBuilder.newClient( dhis2ApiUrl, pat ).build();
        }
        else if ( StringUtils.hasText( username ) && StringUtils.hasText( password ) )
        {
            dhis2Client = Dhis2ClientBuilder.newClient( dhis2ApiUrl, username, password ).build();
        }
        else
        {
            terminate(
                "Missing DHIS2 authentication details. Are you sure that you set `dhis2.api.pat` or both `dhis2.api.username` and `dhis2.api.password`?" );
        }

        if ( testConnectionOnStartUp )
        {
            testDhis2Connection( dhis2Client );
        }
        return dhis2Client;
    }

    protected void testRapidProConnection()
        throws
        IOException
    {
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        HttpUrl httpUrl = HttpUrl.parse( rapidProApiUrl + "/workspace.json" );
        HttpUrl.Builder httpUrlBuilder = httpUrl.newBuilder();

        Request request = new Request.Builder().url( httpUrlBuilder.build() )
            .addHeader( "Authorization", "Token " + rapidProApiToken ).get().build();
        okhttp3.Response response = null;
        try
        {
            try
            {
                response = okHttpClient.newCall( request ).execute();
            }
            catch ( IOException e )
            {
                terminate( String.format(
                    "Connection error during RapidPro connection test. Are you sure that `rapidpro.api.url` is set correctly? Hint: check your firewall settings. Error message => %s",
                    e.getMessage() ) );
            }

            if ( response.isSuccessful() )
            {
                String workspaceAsJson = null;
                Map<String, Object> workspace = null;
                try
                {
                    workspaceAsJson = response.body().string();
                    workspace = new ObjectMapper().readValue( workspaceAsJson, Map.class );
                }
                catch ( Exception e )
                {
                    Throwable t = NestedExceptionUtils.getRootCause( e );
                    if ( t instanceof JsonParseException )
                    {
                        terminate( String.format(
                            "Bad JSON in response during RapidPro connection test. Are you sure that `rapidpro.api.url` is set correctly? Error message => %s",
                            t.getMessage() ) );
                    }
                    else
                    {
                        throw new Dhis2RapidProException( e );
                    }
                }
                if ( workspace != null && workspace.get( "uuid" ) != null )
                {
                    LOGGER.info( "Successfully tested connection to RapidPro" );
                }
                else
                {
                    terminate( String.format(
                        "Unexpected JSON response during RapidPro connection test: expecting workspace UUID. Are you sure that `rapidpro.api.url` is set correctly and the right version of RapidPro is installed? JSON response => %s",
                        workspaceAsJson ) );
                }
            }
            else
            {
                terminate( String.format(
                    "Unexpected HTTP response code during RapidPro connection test. Are you sure that `rapidpro.api.url` is set correctly and the credentials are valid? Response code: %s. Response body: %s",
                    response.code(), response.body() != null ? response.body().string() : "" ) );
            }
        }
        finally
        {
            if ( response != null )
            {
                response.close();
            }
        }
    }

    protected void testDhis2Connection( Dhis2Client dhis2Client )
    {
        Dhis2Response dhis2Response = null;
        try
        {
            dhis2Response = dhis2Client.get( "system/info" ).transfer();
        }
        catch ( Dhis2ClientException e )
        {
            Throwable t = NestedExceptionUtils.getRootCause( e );
            if ( t instanceof Dhis2ClientException )
            {
                terminate( String.format(
                    "Unexpected HTTP response code during DHIS2 connection test. Are you sure that `dhis2.api.url` is set correctly and the credentials are valid? Hint: check your firewall settings. Error message => %s",
                    t.getMessage() ) );
            }
            else if ( t instanceof IOException )
            {
                terminate( String.format(
                    "Connection error during DHIS2 connection test. Are you sure that `dhis2.api.url` is set correctly? Hint: check your firewall settings. Error message => %s",
                    e.getMessage() ) );
            }
            else
            {
                throw new Dhis2RapidProException( e );
            }
        }
        if ( dhis2Response != null )
        {
            String systemInfoAsJson = null;
            Map<String, Object> systemInfo = null;
            try
            {
                systemInfoAsJson = new String( dhis2Response.read().readAllBytes() );
                systemInfo = new ObjectMapper().readValue( systemInfoAsJson, Map.class );
            }
            catch ( Exception e )
            {
                Throwable t = NestedExceptionUtils.getRootCause( e );
                if ( t instanceof JsonParseException )
                {
                    terminate( String.format(
                        "Bad JSON in response during DHIS2 connection test. Are you sure that `dhis2.api.url` is set correctly? Error message => %s",
                        t.getMessage() ) );
                }
                else
                {
                    throw new Dhis2RapidProException( e );
                }
            }
            if ( systemInfo != null && systemInfo.get( "version" ) != null )
            {
                LOGGER.info( "Successfully tested connection to DHIS " + systemInfo.get( "version" ) );
            }
            else
            {
                terminate( String.format(
                    "Unexpected JSON response during DHIS2 connection test: expecting system info version. Are you sure that `dhis2.api.url` is set correctly and the right version of DHIS is installed? JSON response => %s",
                    systemInfoAsJson ) );
            }
        }
        try
        {
            dhis2Response.close();
        }
        catch ( IOException e )
        {
            throw new Dhis2RapidProException( e );
        }
    }

    protected void terminate( String shutdownMessage )
    {
        LOGGER.error( "TERMINATING!!! " + shutdownMessage );
        applicationContext.close();
        System.exit( 1 );
    }

    @Bean
    public ObjectMapper objectMapper()
    {
        return new ObjectMapper().registerModule( new Jdk8Module() );
    }

    @Bean
    public ArtemisConfigurationCustomizer artemisConfigurationCustomizer()
    {
        return configuration -> {
            try
            {
                DatabaseStorageConfiguration databaseStorageConfiguration = new DatabaseStorageConfiguration();

                databaseStorageConfiguration.setDataSource( dataSource );
                databaseStorageConfiguration.setLargeMessageTableName( "large_messages" );
                databaseStorageConfiguration.setPageStoreTableName( "page_store" );
                databaseStorageConfiguration.setBindingsTableName( "bindings" );
                databaseStorageConfiguration.setMessageTableName( "messages" );
                databaseStorageConfiguration.setNodeManagerStoreTableName( "node_manager_store" );

                configuration.setStoreConfiguration( databaseStorageConfiguration );
                configuration.setPersistenceEnabled( true );
            }
            catch ( Exception e )
            {
                throw new RuntimeException( e );
            }
        };
    }

    public String getRapidProApiUrl()
    {
        return rapidProApiUrl;
    }

    public void setRapidProApiUrl( String rapidProApiUrl )
    {
        this.rapidProApiUrl = rapidProApiUrl;
    }

    public String getRapidProApiToken()
    {
        return rapidProApiToken;
    }

    public void setRapidProApiToken( String rapidProApiToken )
    {
        this.rapidProApiToken = rapidProApiToken;
    }

    public ApplicationArguments getApplicationArguments()
    {
        return applicationArguments;
    }

    public void setApplicationArguments( ApplicationArguments applicationArguments )
    {
        this.applicationArguments = applicationArguments;
    }

    public ConfigurableApplicationContext getApplicationContext()
    {
        return applicationContext;
    }

    public void setApplicationContext( ConfigurableApplicationContext applicationContext )
    {
        this.applicationContext = applicationContext;
    }

    public Boolean getTestConnectionOnStartUp()
    {
        return testConnectionOnStartUp;
    }

    public void setTestConnectionOnStartUp( Boolean testConnectionOnStartUp )
    {
        this.testConnectionOnStartUp = testConnectionOnStartUp;
    }

    protected boolean isNullOrEmpty( List<String> arg )
    {
        return arg == null || arg.isEmpty();
    }
}
