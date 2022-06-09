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

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.hisp.dhis.api.v2_37_6.model.DescriptiveWebMessage;
import org.hisp.dhis.api.v2_37_6.model.OrganisationUnit;
import org.hisp.dhis.api.v2_37_6.model.OrganisationUnitLevel;
import org.hisp.dhis.api.v2_37_6.model.User;
import org.hisp.dhis.api.v2_37_6.model.UserAuthorityGroup;
import org.hisp.dhis.api.v2_37_6.model.UserCredentials;
import org.hisp.dhis.api.v2_37_6.model.WebMessage;
import org.hisp.dhis.integration.sdk.Dhis2Client;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.SocketUtils;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import com.github.javafaker.Faker;
import com.github.javafaker.Name;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public final class Environment
{
    public static final String ORG_UNIT_ID;

    public static final Dhis2Client DHIS2_CLIENT;

    private static final Logger LOGGER = LoggerFactory.getLogger( Environment.class );

    private static final Network RAPIDPRO_NETWORK = Network.builder().id( "rapidProNetwork" ).build();

    private static final Network DHIS2_NETWORK = Network.builder().id( "dhis2Network" ).build();

    private static GenericContainer<?> REDIS_CONTAINER;

    private static GenericContainer<?> ELASTICSEARCH_CONTAINER;

    private static GenericContainer<?> RAPIDPRO_CONTAINER;

    private static GenericContainer<?> MAILROOM_CONTAINER;

    private static GenericContainer<?> DHIS2_CONTAINER;

    public static final RequestSpecification RAPIDPRO_REQUEST_SPEC;

    public static final RequestSpecification RAPIDPRO_API_REQUEST_SPEC;

    public static final String RAPIDPRO_CONNECTOR_HTTP_ENDPOINT_URI;

    private static PostgreSQLContainer<?> DHIS2_DB_CONTAINER;

    static
    {
        try
        {
            composeRapidProContainers();
            composeDhis2Containers();
            startContainers();

            RAPIDPRO_CONTAINER.execInContainer(
                "sh", "-c", "python manage.py createsuperuser --username root --email admin@dhis2.org --noinput" );

            String rapidProBaseUri = String.format( "http://%s:%s", RAPIDPRO_CONTAINER.getHost(),
                RAPIDPRO_CONTAINER.getFirstMappedPort() );

            String rapidProApiUrl = rapidProBaseUri + "/api/v2";

            String dhis2ApiUrl = String.format( "http://%s:%s/api", DHIS2_CONTAINER.getHost(),
                DHIS2_CONTAINER.getFirstMappedPort() );

            System.setProperty( "dhis2.api.url", dhis2ApiUrl );
            System.setProperty( "rapidpro.api.url", rapidProApiUrl );

            RAPIDPRO_REQUEST_SPEC = new RequestSpecBuilder().setBaseUri( rapidProBaseUri ).build();
            RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

            given( RAPIDPRO_REQUEST_SPEC ).contentType( ContentType.URLENC ).formParams(
                Map.of( "first_name", "Alice", "last_name", "Wonderland", "email", "claude@dhis2.org", "password",
                    "12345678", "timezone", "Europe/Berlin", "name", "dhis2" ) )
                .when()
                .post( "/org/signup/" ).then().statusCode( 302 );

            String apiToken = generateRapidProApiToken();
            RAPIDPRO_API_REQUEST_SPEC = new RequestSpecBuilder().setBaseUri( rapidProApiUrl )
                .addHeader( "Authorization", "Token " + apiToken ).build();
            System.setProperty( "rapidpro.api.token", apiToken );
            RAPIDPRO_CONNECTOR_HTTP_ENDPOINT_URI = String.format( "http://0.0.0.0:%s/rapidProConnector",
                SocketUtils.findAvailableTcpPort() );
            System.setProperty( "http.endpoint.uri", RAPIDPRO_CONNECTOR_HTTP_ENDPOINT_URI );

            DHIS2_CLIENT = Dhis2ClientBuilder.newClient( dhis2ApiUrl, "admin", "district" ).build();

            ORG_UNIT_ID = createOrgUnit();
            createOrgUnitLevel();
            String orgUnitLevelId = null;
            for ( OrganisationUnitLevel organisationUnitLevel : DHIS2_CLIENT.get( "organisationUnitLevels" )
                .withFields( "id" )
                .withoutPaging().transfer().returnAs( OrganisationUnitLevel.class, "organisationUnitLevels" ) )
            {
                orgUnitLevelId = organisationUnitLevel.getId().get();
            }

            importMetaData( Objects.requireNonNull( orgUnitLevelId ) );
            addOrgUnitToAdminUser( ORG_UNIT_ID );
            createDhis2Users( ORG_UNIT_ID );
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new RuntimeException( e );
        }
    }

    private Environment()
    {

    }

    private static void startContainers()
    {
        Stream.of( REDIS_CONTAINER, ELASTICSEARCH_CONTAINER, RAPIDPRO_CONTAINER,
            MAILROOM_CONTAINER, DHIS2_DB_CONTAINER, DHIS2_CONTAINER ).parallel().forEach( GenericContainer::start );
    }

    private static void composeDhis2Containers()
    {
        DHIS2_DB_CONTAINER = newPostgreSQLContainer( "dhis2", "dhis", "dhis", DHIS2_NETWORK );

        DHIS2_CONTAINER = new GenericContainer<>(
            "dhis2/core:2.36.10-tomcat-8.5.34-jre8-alpine" )
                .withClasspathResourceMapping( "dhis.conf", "/DHIS2_home/dhis.conf", BindMode.READ_WRITE )
                .withNetwork( DHIS2_NETWORK ).withExposedPorts( 8080 )
                .waitingFor( new HttpWaitStrategy().forStatusCode( 200 ).withStartupTimeout( Duration.ofMinutes( 3 ) ) )
                .withEnv( "WAIT_FOR_DB_CONTAINER", "db" + ":" + 5432 + " -t 0" );
    }

    private static void composeRapidProContainers()
    {
        PostgreSQLContainer<?> rapidProDbContainer = newPostgreSQLContainer( "temba", "temba", "temba",
            RAPIDPRO_NETWORK );

        REDIS_CONTAINER = new GenericContainer<>(
            DockerImageName.parse( "redis:6.2.6-alpine" ) )
                .withNetworkAliases( "redis" )
                .withExposedPorts( 6379 )
                .withNetwork( RAPIDPRO_NETWORK );

        ELASTICSEARCH_CONTAINER = new GenericContainer<>(
            DockerImageName.parse( "elasticsearch:6.8.23" ) )
                .withEnv( "discovery.type", "single-node" )
                .withNetwork( RAPIDPRO_NETWORK )
                .withNetworkAliases( "elasticsearch" )
                .withExposedPorts( 9200 )
                .waitingFor( new HttpWaitStrategy().forStatusCode( 200 ) );

        RAPIDPRO_CONTAINER = new GenericContainer<>(
            DockerImageName.parse( "praekeltfoundation/rapidpro:v7.2.4" ) )
                .dependsOn( rapidProDbContainer )
                .withExposedPorts( 8000 )
                .withNetwork( RAPIDPRO_NETWORK )
                .waitingFor( new HttpWaitStrategy().forStatusCode( 200 ).withStartupTimeout( Duration.ofMinutes( 3 ) ) )
                .withEnv( "SECRET_KEY", "super-secret-key" )
                .withEnv( "DATABASE_URL", "postgresql://temba:temba@db/temba" )
                .withEnv( "REDIS_URL", "redis://redis:6379/0" )
                .withEnv( "DJANGO_DEBUG", "on" )
                .withEnv( "DOMAIN_NAME", "localhost" )
                .withEnv( "MANAGEPY_COLLECTSTATIC", "on" )
                .withEnv( "MANAGEPY_INIT_DB", "on" )
                .withEnv( "MANAGEPY_MIGRATE", "on" )
                .withEnv( "DJANGO_SUPERUSER_PASSWORD", "12345678" )
                .withEnv( "MAILROOM_URL", "http://mailroom:8090" )
                .withEnv( "MAILROOM_AUTH_TOKEN", "Gqcqvi2PGkoZMpQi" )
                .withEnv( "ELASTICSEARCH_URL", "http://elasticsearch:9200" )
                .withCommand( "sh", "-c",
                    "sed -i '/CsrfViewMiddleware/s/^/#/g' temba/settings_common.py && /startup.sh" );

        MAILROOM_CONTAINER = new GenericContainer<>(
            DockerImageName.parse( "praekeltfoundation/mailroom:v7.0.1" ) )
                .withNetwork( RAPIDPRO_NETWORK )
                .withNetworkAliases( "mailroom" )
                .withEnv( "MAILROOM_DOMAIN", "mailroom" )
                .withEnv( "MAILROOM_ELASTIC", "http://elasticsearch:9200" )
                .withEnv( "MAILROOM_ATTACHMENT_DOMAIN", "mailroom" )
                .withEnv( "MAILROOM_AUTH_TOKEN", "Gqcqvi2PGkoZMpQi" )
                .withEnv( "MAILROOM_DB", "postgres://temba:temba@db/temba?sslmode=disable" )
                .withEnv( "MAILROOM_REDIS", "redis://redis:6379/0" )
                .withCommand( "mailroom", "--address", "0.0.0.0" );
    }

    private static PostgreSQLContainer<?> newPostgreSQLContainer( String databaseName,
        String username, String password, Network network )
    {
        return new PostgreSQLContainer<>(
            DockerImageName.parse( "postgis/postgis:12-3.2-alpine" ).asCompatibleSubstituteFor( "postgres" ) )
                .withDatabaseName( databaseName )
                .withNetworkAliases( "db" )
                .withUsername( username )
                .withPassword( password ).withNetwork( network );
    }

    private static void addOrgUnitToAdminUser( String orgUnitId )
        throws IOException
    {
        DHIS2_CLIENT.post( "users/M5zQapPyTZI/organisationUnits/{organisationUnitId}", orgUnitId ).transfer().close();
    }

    private static void createDhis2Users( String orgUnitId )
    {
        int phoneNumber = 21000000;
        Faker faker = new Faker();
        for ( int i = 0; i < 10; i++ )
        {
            Name name = faker.name();
            DHIS2_CLIENT.post( "users" )
                .withResource( new User().withFirstName( name.firstName() ).withSurname( name.lastName() )
                    .withPhoneNumber( "00356" + phoneNumber )
                    .withAttributeValues( Collections.emptyList() )
                    .withOrganisationUnits( List.of( new OrganisationUnit().withId( orgUnitId ) ) )
                    .withUserCredentials(
                        new UserCredentials().withCatDimensionConstraints( Collections.emptyList() )
                            .withCogsDimensionConstraints( Collections.emptyList() ).withUsername( name.username() )
                            .withPassword( "aKa9CD8HyAi8Y7!" ).withUserRoles(
                                List.of( new UserAuthorityGroup().withId( "yrB6vc5Ip3r" ) ) ) ) )
                .transfer();
            phoneNumber++;
        }
    }

    private static String generateRapidProApiToken()
    {
        ExtractableResponse<Response> loginPageResponse = given( RAPIDPRO_REQUEST_SPEC ).when()
            .get( "/users/login/" ).then().statusCode( 200 ).extract();

        String csrfMiddlewareToken = loginPageResponse.htmlPath()
            .getString( "html.body.div.div[3].div.div.div[1].div.div.form.input.@value" );
        String csrfToken = loginPageResponse.cookie( "csrftoken" );

        String sessionId = given( RAPIDPRO_REQUEST_SPEC ).contentType( ContentType.URLENC )
            .cookie( "csrftoken", csrfToken )
            .formParams( Map.of( "csrfmiddlewaretoken", csrfMiddlewareToken,
                "username", "root", "password", "12345678" ) )
            .when()
            .post( "/users/login/" ).then().statusCode( 302 ).extract().cookie( "sessionid" );

        return given( RAPIDPRO_REQUEST_SPEC )
            .cookie( "sessionid", sessionId ).when()
            .post( "/api/apitoken/refresh/" ).then().statusCode( 200 ).extract().path( "token" );
    }

    private static void importMetaData( String orgUnitLevelId )
        throws IOException
    {
        String metaData = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "MLAG00_1.2.1_DHIS2.36.json" ),
            Charset.defaultCharset() ).replaceAll( "<OU_LEVEL_DISTRICT_UID>", orgUnitLevelId )
            .replaceAll( "<OU_LEVEL_FACILITY_UID>", orgUnitLevelId );

        WebMessage webMessage = DHIS2_CLIENT.post( "metadata" )
            .withResource( metaData )
            .withParameter( "atomicMode", "NONE" ).transfer().returnAs( WebMessage.class );

        assertEquals( DescriptiveWebMessage.Status.OK, webMessage.getStatus().get() );
    }

    private static void createOrgUnitLevel()
        throws IOException
    {
        DHIS2_CLIENT.post( "filledOrganisationUnitLevels" )
            .withResource( Map.of( "organisationUnitLevels",
                List.of( new OrganisationUnitLevel().withName( "Level 1" ).withLevel( 1 ) ) ) )
            .transfer().close();
    }

    private static String createOrgUnit()
    {
        return (String) ((Map<String, Object>) DHIS2_CLIENT.post( "organisationUnits" ).withResource(
            new OrganisationUnit().withName( "Acme" ).withShortName( "Acme" ).withOpeningDate( new Date() ) ).transfer()
            .returnAs( WebMessage.class ).getResponse().get()).get( "uid" );
    }

}
