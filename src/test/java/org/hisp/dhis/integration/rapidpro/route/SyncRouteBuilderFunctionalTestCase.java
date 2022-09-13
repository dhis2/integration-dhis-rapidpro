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
package org.hisp.dhis.integration.rapidpro.route;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spring.boot.SpringBootCamelContext;
import org.hisp.dhis.api.model.v2_37_7.DescriptiveWebMessage;
import org.hisp.dhis.api.model.v2_37_7.ImportReportWebMessageResponse;
import org.hisp.dhis.api.model.v2_37_7.User;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.rapidpro.SelfSignedHttpClientConfigurer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SyncRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Override
    public void doBeforeEach()
        throws
        IOException
    {
        Environment.deleteDhis2Users();
        Environment.createDhis2Users( Environment.ORG_UNIT_ID );
    }

    @Test
    public void testContactSynchronisationFailsGivenThatSyncPropertyIsFalse()
    {
        System.setProperty( "sync.rapidpro.contacts", "false" );
        camelContext.start();
        CamelExecutionException e = assertThrows(
            CamelExecutionException.class, () -> producerTemplate.sendBody( "direct:sync", null ) );
        assertEquals( DirectConsumerNotAvailableException.class, e.getCause().getClass() );
    }

    @Test
    public void testFirstSynchronisationCreatesContacts()
    {
        camelContext.start();
        assertPreCondition();
        producerTemplate.sendBody( "direct:sync", null );
        assertPostCondition();
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 10 ) )
            .body( "results[0].fields.dhis2_organisation_unit_id", equalTo( Environment.ORG_UNIT_ID ) );
    }

    @Test
    public void testNewContactSynchronisationGivenInvalidPhoneNumber()
    {
        String invalidPhoneNoUserId = Environment.createDhis2User( Environment.ORG_UNIT_ID, "Invalid" );
        CountDownLatch expectedLogMessage = new CountDownLatch( 2 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "WARN" ) && message.startsWith(
                    String.format(
                        "Unexpected status code when creating RapidPro contact for DHIS2 user %s => HTTP 400.",
                        invalidPhoneNoUserId ) ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );

        camelContext.start();
        assertPreCondition();
        producerTemplate.sendBody( "direct:sync", null );
        assertPostCondition();

        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 10 ) )
            .body( "results[0].fields.dhis2_organisation_unit_id", equalTo( Environment.ORG_UNIT_ID ) );

        assertEquals( 1, expectedLogMessage.getCount() );
    }

    @Test
    public void testManualContactSynchronisation()
    {
        camelContext.getRegistry().bind( "selfSignedHttpClientConfigurer", new SelfSignedHttpClientConfigurer() );
        camelContext.start();

        assertPreCondition();
        String response = producerTemplate.requestBodyAndHeader(
            rapidProConnectorHttpEndpointUri + "/sync?httpClientConfigurer=#selfSignedHttpClientConfigurer",
            null,
            "Authorization",
            "Basic " + Base64.getEncoder().encodeToString( "dhis2rapidpro:dhis2rapidpro".getBytes() ), String.class );
        assertEquals( "<html><body>Synchronised RapidPro contacts with DHIS2 users</body></html>", response );

        assertPostCondition();
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 10 ) );
    }

    @Test
    public void testContactSynchronisationGivenOrgUnitIdSchemeIsCode()
    {
        System.setProperty( "org.unit.id.scheme", "CODE" );
        camelContext.start();

        assertPreCondition();
        producerTemplate.sendBody( "direct:sync", null );
        assertPostCondition();
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 10 ) )
            .body( "results[0].fields.dhis2_organisation_unit_id", equalTo( "ACME" ) );
    }

    @Test
    public void testNextSynchronisationUpdatesRapidProContactGivenUpdatedDhis2User()
    {
        camelContext.start();
        assertPreCondition();

        producerTemplate.sendBody( "direct:sync", null );

        User user = updateDhis2User( "0035661000000" );

        producerTemplate.sendBody( "direct:sync", null );

        Map<String, Object> contactUnderTest = null;
        for ( Map<String, Object> contact : fetchRapidProContacts() )
        {
            Map<String, Object> fields = (Map<String, Object>) contact.get( "fields" );
            if ( fields.get( "dhis2_user_id" ).equals( user.getId().get() ) )
            {
                contactUnderTest = contact;
            }
        }

        assertEquals( "tel:+35661000000", ((List) contactUnderTest.get( "urns" )).get( 0 ) );
    }

    @Test
    public void testUpdateContactSynchronisationGivenInvalidPhoneNumber()
    {
        assertPreCondition();

        CountDownLatch expectedLogMessage = new CountDownLatch( 2 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "WARN" ) && message.startsWith(
                    "Unexpected status code when updating RapidPro contact " ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );

        camelContext.start();
        producerTemplate.sendBody( "direct:sync", null );
        assertEquals( 2, expectedLogMessage.getCount() );

        updateDhis2User( "invalid" );
        producerTemplate.sendBody( "direct:sync", null );

        assertEquals( 1, expectedLogMessage.getCount() );
    }

    private void assertPreCondition()
    {
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 0 ) );
    }

    private void assertPostCondition()
    {
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "fields.json" ).then()
            .body( "results.size()", equalTo( 2 ) )
            .body( "results[1].key", equalTo( "dhis2_organisation_unit_id" ) );
    }

    private User updateDhis2User( String phoneNumber )
    {
        List<User> users = new ArrayList<>();
        Iterable<User> usersIterable = Environment.DHIS2_CLIENT.get( "users" ).withFilter( "phoneNumber:!null" )
            .withFields( "*" ).withoutPaging()
            .transfer()
            .returnAs( User.class, "users" );
        usersIterable.forEach( users::add );
        User user = users.get( ThreadLocalRandom.current().nextInt( 0, users.size() ) );
        user.setPhoneNumber( phoneNumber );
        ImportReportWebMessageResponse importReportWebMessageResponse = Environment.DHIS2_CLIENT.put( "users/{id}",
                user.getId().get() )
            .withResource( user ).transfer()
            .returnAs(
                ImportReportWebMessageResponse.class );
        assertEquals( DescriptiveWebMessage.Status.OK, importReportWebMessageResponse.getStatus().get() );

        return user;
    }
}
