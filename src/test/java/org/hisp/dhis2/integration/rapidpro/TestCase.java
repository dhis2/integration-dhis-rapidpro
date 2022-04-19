package org.hisp.dhis2.integration.rapidpro;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;

@SpringBootTest
@CamelSpringBootTest
@Testcontainers
public class TestCase
{
    private static final Network RAPIDPRO_NETWORK = Network.newNetwork();
    private static final Network DHIS2_NETWORK = Network.newNetwork();

    @Container
    public static final PostgreSQLContainer<?> RAPIDPRO_DB_CONTAINER = new PostgreSQLContainer<>(
        DockerImageName.parse( "postgis/postgis:12-3.2-alpine" ).asCompatibleSubstituteFor( "postgres" ) )
        .withDatabaseName( "temba" )
        .withNetworkAliases( "db" )
        .withUsername( "temba" )
        .withPassword( "temba" ).withNetwork( RAPIDPRO_NETWORK );

    @Container
    public static final PostgreSQLContainer<?> DHIS2_DB_CONTAINER = new PostgreSQLContainer<>(
        DockerImageName.parse( "postgis/postgis:12-3.2-alpine" ).asCompatibleSubstituteFor( "postgres" ) )
        .withDatabaseName( "dhis2" )
        .withNetworkAliases( "db" )
        .withUsername( "dhis" )
        .withPassword( "dhis" ).withNetwork( DHIS2_NETWORK );

    @Container
    public static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
        DockerImageName.parse( "redis:6.2.6-alpine" ) )
        .withNetworkAliases( "redis" )
        .withExposedPorts( 6379 )
        .withNetwork( RAPIDPRO_NETWORK );

    @Container
    public static final GenericContainer<?> RAPIDPRO_CONTAINER = new GenericContainer<>(
        DockerImageName.parse( "praekeltfoundation/rapidpro:v7.2.4" ) )
        .dependsOn( RAPIDPRO_DB_CONTAINER, REDIS_CONTAINER )
        .withExposedPorts( 8000 )
        .withNetwork( RAPIDPRO_NETWORK )
        .waitingFor( new HttpWaitStrategy().forStatusCode( 200 ).withStartupTimeout( Duration.ofMinutes( 2 ) ) )
        .withEnv( "SECRET_KEY", "1234" ).withEnv( "DATABASE_URL", "postgresql://temba:temba@db/temba" )
        .withEnv( "REDIS_URL", "redis://redis:6379/0" )
        .withEnv( "DJANGO_DEBUG", "on" )
        .withEnv( "DOMAIN_NAME", "localhost" )
        .withEnv( "MANAGEPY_INIT_DB", "on" )
        .withEnv( "MANAGEPY_MIGRATE", "on" )
        .withEnv( "DJANGO_SUPERUSER_PASSWORD", "12345678" )
        .withCommand( "sh", "-c", "sed -i '/CsrfViewMiddleware/s/^/#/g' temba/settings_common.py && /startup.sh" );

    @Container
    public static final GenericContainer<?> DHIS2_CONTAINER = new GenericContainer<>( "dhis2/core:2.37.6-tomcat-8.5.34-jre8-alpine" )
        .dependsOn( DHIS2_DB_CONTAINER )
        .withClasspathResourceMapping( "dhis.conf", "/DHIS2_home/dhis.conf", BindMode.READ_WRITE )
        .withNetwork( DHIS2_NETWORK ).withExposedPorts( 8080 ).waitingFor( new HttpWaitStrategy().forStatusCode( 200 ) )
        .withEnv( "WAIT_FOR_DB_CONTAINER", "db" + ":" + 5432 + " -t 0" );

    @BeforeAll
    public static void beforeAll()
        throws IOException, InterruptedException
    {
        RAPIDPRO_CONTAINER.execInContainer(
            "sh", "-c", "python manage.py createsuperuser --username root --email admin@dhis2.org --noinput" );
        RestAssured.baseURI = "http://" + RAPIDPRO_CONTAINER.getHost() + ":" + RAPIDPRO_CONTAINER.getFirstMappedPort();
        RestAssured.requestSpecification = new RequestSpecBuilder().build().contentType( ContentType.JSON );
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        given().contentType( ContentType.URLENC ).formParams( Map.of( "first_name", "Alice", "last_name", "Wonderland", "email", "claude@dhis2.org", "password",
                "12345678", "timezone", "Europe/Berlin", "name", "dhis2" ) ).when()
            .post( "/org/signup/" ).then().statusCode( 302 );
    }

    @Test
    public void test()
        throws InterruptedException
    {


    }
}
