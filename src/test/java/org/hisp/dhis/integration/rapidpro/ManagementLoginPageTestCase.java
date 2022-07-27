package org.hisp.dhis.integration.rapidpro;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static io.restassured.RestAssured.given;

@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT )
@ActiveProfiles( "test" )
@TestPropertySource( properties = { "dhis2.api.url=", "rapidpro.api.token=", "rapidpro.api.url=",
    "camel.springboot.auto-startup=false" } )
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
public class ManagementLoginPageTestCase
{
    @LocalServerPort
    private int serverPort;

    private RequestSpecification loginPageRequestSpec;

    @BeforeEach
    public void doBeforeEach()
    {
        loginPageRequestSpec = new RequestSpecBuilder().setBaseUri(
            String.format( "https://localhost:%s/login", serverPort ) ).setRelaxedHTTPSValidation().build();
    }

    @Test
    public void tesGetLoginPage()
    {
        given( loginPageRequestSpec ).get().then().statusCode( 200 );
    }
}
