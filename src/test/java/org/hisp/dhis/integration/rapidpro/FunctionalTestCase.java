package org.hisp.dhis.integration.rapidpro;

import com.github.javafaker.Faker;
import io.restassured.specification.RequestSpecification;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.hisp.dhis.api.v2_37_4.model.DescriptiveWebMessage;
import org.hisp.dhis.api.v2_37_4.model.ImportReport;
import org.hisp.dhis.api.v2_37_4.model.ImportReportWebMessageResponse;
import org.hisp.dhis.api.v2_37_4.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@CamelSpringBootTest
@Testcontainers
@UseAdviceWith
public class FunctionalTestCase
{
    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    private static RequestSpecification RAPIDPRO_API_REQUEST_SPEC;

    @BeforeAll
    public static void beforeAll()
    {
        RAPIDPRO_API_REQUEST_SPEC = Environment.RAPIDPRO_API_REQUEST_SPEC;
    }

    @BeforeEach
    public void beforeEach()
    {
        if ( !camelContext.isStarted() )
        {
            camelContext.start();
        }

        for ( Map<String, Object> contact : fetchRapidProContacts() )
        {
            given( Environment.RAPIDPRO_API_REQUEST_SPEC ).delete( "/contacts.json?uuid={uuid}",
                    contact.get( "uuid" ) )
                .then()
                .statusCode( 204 );
        }
    }

    @Test
    public void testFirstSynchronisationCreatesContacts()
        throws InterruptedException
    {
        assertPreCondition();
        producerTemplate.sendBody( "direct:sync", null );
        assertPostCondition();
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 10 ) );
    }

    @Test
    public void testNextSynchronisationUpdatesRapidProContactGivenUpdatedDhis2User()
        throws InterruptedException
    {
        assertPreCondition();

        producerTemplate.sendBody( "direct:sync", null );

        List<User> users = new ArrayList<>();
        Iterable<User> usersIterable = Environment.DHIS2_CLIENT.get( "users" ).withFields( "*" ).withoutPaging().transfer()
            .returnAs( User.class, "users" );
        usersIterable.forEach( users::add );
        User user = users.get( ThreadLocalRandom.current().nextInt( 0, users.size() ) );
        String oldName = user.getFirstName().get();
        user.setFirstName( new Faker().name().firstName() );
        ImportReportWebMessageResponse importReportWebMessageResponse = Environment.DHIS2_CLIENT.put( "users/{id}",
                user.getId().get() )
            .withResource( user ).transfer()
            .returnAs(
                ImportReportWebMessageResponse.class );
                assertEquals( DescriptiveWebMessage.Status.OK, importReportWebMessageResponse.getStatus().get() );

        producerTemplate.sendBody( "direct:sync", null );

        Map<String, Object> contactUnderTest = null;
        for ( Map<String, Object> contact : fetchRapidProContacts() )
        {
            Map<String, Object> fields = (Map<String, Object>) contact.get( "fields" );
            if (fields.get( "dhis2_user_id" ).equals( user.getId().get() )) {
                contactUnderTest = contact;
            }
        }

        assertEquals(user.getFirstName().get() + " " + user.getSurname().get(), contactUnderTest.get( "name" ));
    }

    private void assertPreCondition()
    {
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 0 ) );
    }

    private void assertPostCondition()
        throws InterruptedException
    {
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "fields.json" ).then()
            .body( "results.size()", equalTo( 2 ) )
            .body( "results[1].key", equalTo( "dhis2_organisation_unit_id" ) );
    }

    private List<Map<String, Object>> fetchRapidProContacts() {
        Map<String, Object> contacts = given( Environment.RAPIDPRO_API_REQUEST_SPEC ).get( "/contacts.json" ).then()
            .statusCode( 200 ).extract()
            .body().as(
                Map.class );
        return (List<Map<String, Object>>) contacts.get( "results" );
    }
}
