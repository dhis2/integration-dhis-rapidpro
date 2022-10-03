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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.restassured.builder.MultiPartSpecBuilder;
import org.hisp.dhis.api.model.v2_36_11.DescriptiveWebMessage;
import org.hisp.dhis.api.model.v2_36_11.ImportReport;
import org.hisp.dhis.api.model.v2_36_11.Notification;
import org.hisp.dhis.api.model.v2_36_11.OrganisationUnit;
import org.hisp.dhis.api.model.v2_36_11.OrganisationUnitLevel;
import org.hisp.dhis.api.model.v2_36_11.User;
import org.hisp.dhis.api.model.v2_36_11.UserAuthorityGroup;
import org.hisp.dhis.api.model.v2_36_11.UserCredentials;
import org.hisp.dhis.api.model.v2_36_11.WebMessage;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
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
    public static String ORG_UNIT_ID;

    public static Dhis2Client DHIS2_CLIENT;

    public static GenericContainer<?> DHIS2_CONTAINER;

    private static final Logger LOGGER = LoggerFactory.getLogger( Environment.class );

    private static final Network RAPIDPRO_NETWORK = Network.builder().build();

    private static final Network DHIS2_NETWORK = Network.builder().build();

    private static GenericContainer<?> REDIS_CONTAINER;

    private static GenericContainer<?> ELASTICSEARCH_CONTAINER;

    private static GenericContainer<?> RAPIDPRO_CONTAINER;

    private static GenericContainer<?> MAILROOM_CONTAINER;

    public static RequestSpecification RAPIDPRO_REQUEST_SPEC;

    public static RequestSpecification RAPIDPRO_API_REQUEST_SPEC;

    private static PostgreSQLContainer<?> DHIS2_DB_CONTAINER;

    static
    {
        try
        {
            RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

            composeRapidProContainers();
            composeDhis2Containers();
            startContainers();

            setUpRapidPro();
            setUpDhis2();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new RuntimeException( e );
        }
    }

    private static void setUpDhis2()
        throws
        IOException,
        InterruptedException
    {
        String dhis2ApiUrl = String.format( "http://%s:%s/api", DHIS2_CONTAINER.getHost(),
            DHIS2_CONTAINER.getFirstMappedPort() );

        System.setProperty( "dhis2.api.url", dhis2ApiUrl );

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
        addOrgUnitToDataSet( ORG_UNIT_ID );
        createDhis2Users( ORG_UNIT_ID );
        runAnalytics();
    }

    private static void setUpRapidPro()
        throws
        IOException,
        InterruptedException
    {
        RAPIDPRO_CONTAINER.execInContainer(
            "sh", "-c", "python manage.py createsuperuser --username root --email admin@dhis2.org --noinput" );

        String rapidProBaseUri = String.format( "http://%s:%s", RAPIDPRO_CONTAINER.getHost(),
            RAPIDPRO_CONTAINER.getFirstMappedPort() );

        String rapidProApiUrl = rapidProBaseUri + "/api/v2";

        System.setProperty( "rapidpro.api.url", rapidProApiUrl );

        RAPIDPRO_REQUEST_SPEC = new RequestSpecBuilder().setBaseUri( rapidProBaseUri ).build();

        given( RAPIDPRO_REQUEST_SPEC ).contentType( ContentType.URLENC ).formParams(
                Map.of( "first_name", "Alice", "last_name", "Wonderland", "email", "claude@dhis2.org", "password",
                    "12345678", "timezone", "Europe/Berlin", "name", "dhis2" ) )
            .when()
            .post( "/org/signup/" ).then().statusCode( 302 );

        importFlowUnderTest();

        String apiToken = generateRapidProApiToken();
        RAPIDPRO_API_REQUEST_SPEC = new RequestSpecBuilder().setBaseUri( rapidProApiUrl )
            .addHeader( "Authorization", "Token " + apiToken ).build();
        System.setProperty( "rapidpro.api.token", apiToken );
    }

    private Environment()
    {

    }

    private static void importFlowUnderTest()
        throws
        IOException
    {
        String flowDefinition = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "flow.json" ),
            Charset.defaultCharset() );

        String sessionId = fetchRapidProSessionId( "claude@dhis2.org", "12345678" );
        ExtractableResponse<Response> flowImportPageResponse = given( RAPIDPRO_REQUEST_SPEC ).cookie( "sessionid",
                sessionId ).
            when().get( "/org/import/" ).then().statusCode( 200 ).extract();
        String flowImportCsrfMiddlewareToken = flowImportPageResponse.htmlPath()
            .getString( "html.body.div.div[4].div.div.div.div[3].form.input.@value" );

        given( RAPIDPRO_REQUEST_SPEC ).cookie( "sessionid", sessionId ).contentType( "multipart/form-data" )
            .multiPart(
                new MultiPartSpecBuilder( flowImportCsrfMiddlewareToken ).emptyFileName().mimeType( "text/plain" )
                    .controlName( "csrfmiddlewaretoken" ).build() )
            .multiPart( new MultiPartSpecBuilder( flowDefinition ).mimeType( "application/json" )
                .header( "Content-Disposition", "form-data; name=\"import_file\"; filename=\"flow.json\"" )
                .header( "Content-Transfer-Encoding", "binary" ).build() )
            .when()
            .post( "/org/import/" ).then().statusCode( 302 ).header( "Location", "/org/home/" );
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
            "dhis2/core:2.36.11.1-tomcat-8.5.34-jre8-alpine" )
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

        ImageFromDockerfile rapidproImage = new ImageFromDockerfile( "rapidpro:7.4.2", false ).withDockerfile(
                Path.of( "rapidpro-docker/rapidpro/Dockerfile" ) ).withBuildArg( "RAPIDPRO_REPO", "rapidpro/rapidpro" )
            .withBuildArg( "RAPIDPRO_VERSION", "v7.4.2" );

        RAPIDPRO_CONTAINER = new GenericContainer<>( rapidproImage )
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

        ImageFromDockerfile mailroomImage = new ImageFromDockerfile( "mailroom:7.4.1", false ).withDockerfile(
                Path.of( "rapidpro-docker/mailroom/Dockerfile" ) ).withBuildArg( "MAILROOM_REPO", "nyaruka/mailroom" )
            .withBuildArg( "MAILROOM_VERSION", "7.4.1" );

        MAILROOM_CONTAINER = new GenericContainer<>(
            mailroomImage )
            .withNetwork( RAPIDPRO_NETWORK )
            .withExposedPorts( 8090 )
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

    public static void runAnalytics()
        throws
        InterruptedException,
        IOException
    {
        DHIS2_CLIENT.post( "maintenance" ).withParameter( "cacheClear", "true" )
            .transfer().close();

        WebMessage webMessage = DHIS2_CLIENT.post( "resourceTables/analytics" ).transfer()
            .returnAs( WebMessage.class );
        String taskId = (String) ((Map<String, Object>) webMessage.getResponse().get()).get( "id" );
        Notification notification = null;
        while ( notification == null || !(Boolean) notification.getCompleted().get() )
        {
            Thread.sleep( 5000 );
            Iterable<Notification> notifications = DHIS2_CLIENT.get( "system/tasks/ANALYTICS_TABLE/{taskId}",
                taskId ).withoutPaging().transfer().returnAs( Notification.class );
            if ( notifications.iterator().hasNext() )
            {
                notification = notifications.iterator().next();
            }
        }
    }

    private static void addOrgUnitToAdminUser( String orgUnitId )
        throws
        IOException
    {
        DHIS2_CLIENT.post( "users/M5zQapPyTZI/organisationUnits/{organisationUnitId}", orgUnitId ).transfer().close();
    }

    private static void addOrgUnitToDataSet( String orgUnitId )
        throws
        IOException
    {
        DHIS2_CLIENT.post( "dataSets/qNtxTrp56wV/organisationUnits/{orgUnitId}", orgUnitId )
            .transfer()
            .close();

        DHIS2_CLIENT.post( "dataSets/VEM58nY22sO/organisationUnits/{orgUnitId}", orgUnitId )
            .transfer()
            .close();
    }

    public static void createDhis2Users( String orgUnitId )
    {
        int phoneNumber = 21000000;
        for ( int i = 0; i < 10; i++ )
        {
            createDhis2User( orgUnitId, "00356" + phoneNumber );
            phoneNumber++;
        }
    }

    public static void deleteDhis2Users()
        throws
        IOException
    {
        Iterable<User> usersIterable = Environment.DHIS2_CLIENT.get( "users" ).withFilter( "phoneNumber:!null" )
            .withFields( "*" ).withoutPaging()
            .transfer()
            .returnAs( User.class, "users" );

        for ( User user : usersIterable )
        {
            Environment.DHIS2_CLIENT.delete( "users/{id}", user.getId().get() ).transfer().close();
        }
    }

    public static String createDhis2User( String orgUnitId, String phoneNumber )
    {
        Faker faker = new Faker();
        Name name = faker.name();

        return DHIS2_CLIENT.post( "users" )
            .withResource( new User().withFirstName( name.firstName() ).withSurname( name.lastName() )
                .withPhoneNumber( phoneNumber )
                .withAttributeValues( Collections.emptyList() )
                .withOrganisationUnits( List.of( new OrganisationUnit().withId( orgUnitId ) ) )
                .withUserCredentials(
                    new UserCredentials().withCatDimensionConstraints( Collections.emptyList() )
                        .withCogsDimensionConstraints( Collections.emptyList() ).withUsername( name.username() )
                        .withPassword( "aKa9CD8HyAi8Y7!" ).withUserRoles(
                            List.of( new UserAuthorityGroup().withId( "yrB6vc5Ip3r" ) ) ) ) )
            .transfer().returnAs( ImportReport.class ).getTypeReports().get().get( 0 ).getObjectReports().get().get( 0 )
            .getUid().get();
    }

    private static String fetchRapidProSessionId( String username, String password )
    {
        ExtractableResponse<Response> loginPageResponse = given( RAPIDPRO_REQUEST_SPEC ).when()
            .get( "/users/login/" ).then().statusCode( 200 ).extract();

        String csrfMiddlewareToken = loginPageResponse.htmlPath()
            .getString( "html.body.div.div[3].div.div.div[1].div.div.form.input.@value" );
        String csrfToken = loginPageResponse.cookie( "csrftoken" );

        return given( RAPIDPRO_REQUEST_SPEC ).contentType( ContentType.URLENC )
            .cookie( "csrftoken", csrfToken )
            .formParams( Map.of( "csrfmiddlewaretoken", csrfMiddlewareToken,
                "username", username, "password", password ) )
            .when()
            .post( "/users/login/" ).then().statusCode( 302 ).extract().cookie( "sessionid" );
    }

    private static String generateRapidProApiToken()
    {
        String sessionId = fetchRapidProSessionId( "root", "12345678" );

        return given( RAPIDPRO_REQUEST_SPEC )
            .cookie( "sessionid", sessionId ).when()
            .post( "/api/apitoken/refresh/" ).then().statusCode( 200 ).extract().path( "token" );
    }

    private static void importMetaData( String orgUnitLevelId )
        throws
        IOException
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
        throws
        IOException
    {
        DHIS2_CLIENT.post( "filledOrganisationUnitLevels" )
            .withResource( Map.of( "organisationUnitLevels",
                List.of( new OrganisationUnitLevel().withName( "Level 1" ).withLevel( 1 ) ) ) )
            .transfer().close();
    }

    private static String createOrgUnit()
    {
        return (String) ((Map<String, Object>) DHIS2_CLIENT.post( "organisationUnits" ).withResource(
                new OrganisationUnit().withName( "Acme" ).withCode( "ACME" ).withShortName( "Acme" )
                    .withOpeningDate( new Date( 964866178L ) ) ).transfer()
            .returnAs( WebMessage.class ).getResponse().get()).get( "uid" );
    }

}
