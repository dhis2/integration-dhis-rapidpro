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

import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.h2.tools.Server;
import org.hisp.dhis.integration.sdk.Dhis2Client;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisProperties;
import org.springframework.context.annotation.Bean;

import java.sql.SQLException;

@SpringBootApplication
public class Application
{
    @Value( "${dhis2.api.url}" )
    private String baseApiUrl;

    @Value( "${dhis2.api.username}" )
    private String username;

    @Value( "${dhis2.api.password}" )
    private String password;

    @Autowired
    private ArtemisProperties artemisProperties;

    public static void main( String[] args )
        throws SQLException
    {
        SpringApplication springApplication = new SpringApplication( Application.class );
        springApplication.setBannerMode( Banner.Mode.OFF );
        springApplication.run( args );
//        Server.createWebServer().start();
    }

    @Bean
    public Dhis2Client dhis2Client()
    {
        return Dhis2ClientBuilder.newClient( baseApiUrl, username, password ).build();
    }

    @Bean
    public ArtemisConfigurationCustomizer artemisConfigurationCustomizer() {
        return configuration -> {
            try {
                DatabaseStorageConfiguration databaseStorageConfiguration = new DatabaseStorageConfiguration();
                databaseStorageConfiguration.setJdbcConnectionUrl("jdbc:h2:./dhis2-rapidpro;AUTO_SERVER=TRUE");
                databaseStorageConfiguration.setJdbcDriverClassName( "org.h2.Driver" );
                configuration.setStoreConfiguration( databaseStorageConfiguration );
                configuration.setPersistenceEnabled( true );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
