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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.hisp.dhis.api.model.v2_37_7.DescriptiveWebMessage;
import org.hisp.dhis.api.model.v2_37_7.ImportReportWebMessageResponse;
import org.hisp.dhis.api.model.v2_37_7.User;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import com.github.javafaker.Faker;

public class SyncRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Test
    @DirtiesContext
    public void testSynchronisationFailsGivenThatSyncDhis2UsersPropertyIsFalse()
    {
        System.setProperty( "sync.dhis2.users", "false" );
        camelContext.start();
        CamelExecutionException e = assertThrows(
            CamelExecutionException.class, () -> producerTemplate.sendBody( "direct:sync", null ) );
        assertEquals( DirectConsumerNotAvailableException.class, e.getCause().getClass() );
    }

    @Test
    @DirtiesContext
    public void testFirstSynchronisationCreatesContacts()
        throws InterruptedException
    {
        camelContext.start();
        assertPreCondition();
        producerTemplate.sendBody( "direct:sync", null );
        assertPostCondition();
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 10 ) );
    }

    @Test
    @DirtiesContext
    public void testNextSynchronisationUpdatesRapidProContactGivenUpdatedDhis2User()
    {
        camelContext.start();
        assertPreCondition();

        producerTemplate.sendBody( "direct:sync", null );

        List<User> users = new ArrayList<>();
        Iterable<User> usersIterable = Environment.DHIS2_CLIENT.get( "users" ).withFilter( "phoneNumber:!null" )
            .withFields( "*" ).withoutPaging()
            .transfer()
            .returnAs( User.class, "users" );
        usersIterable.forEach( users::add );
        User user = users.get( ThreadLocalRandom.current().nextInt( 0, users.size() ) );
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
            if ( fields.get( "dhis2_user_id" ).equals( user.getId().get() ) )
            {
                contactUnderTest = contact;
            }
        }

        assertEquals( user.getFirstName().get() + " " + user.getSurname().get(), contactUnderTest.get( "name" ) );
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
}
