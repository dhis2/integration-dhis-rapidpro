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
public class H2WebConsoleTestCase
{
    @LocalServerPort
    private int serverPort;

    private RequestSpecification h2RequestSpec;

    @BeforeEach
    public void beforeEach()
    {
        h2RequestSpec = new RequestSpecBuilder().setBaseUri(
                String.format( "https://localhost:%s/management/h2-console", serverPort ) ).setRelaxedHTTPSValidation()
            .build();
    }

    @Test
    public void testAnonymousHttpGet()
    {
        given( h2RequestSpec ).get().then().statusCode( 401 );
    }

    @Test
    public void testAuthorisedHttpGet()
    {
        given( h2RequestSpec ).auth().basic( "dhis2rapidpro", "dhis2rapidpro" ).get().then()
            .statusCode( 200 );
    }
}
