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
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.hisp.dhis.api.v2_37_6.model.DataValueSet;
import org.hisp.dhis.api.v2_37_6.model.DataValue__1;
import org.hisp.dhis.api.v2_37_6.model.DescriptiveWebMessage;
import org.hisp.dhis.api.v2_37_6.model.ImportReportWebMessageResponse;
import org.hisp.dhis.api.v2_37_6.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StreamUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.javafaker.Faker;
import io.restassured.specification.RequestSpecification;

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
            given( RAPIDPRO_API_REQUEST_SPEC ).delete( "/contacts.json?uuid={uuid}",
                    contact.get( "uuid" ) )
                .then()
                .statusCode( 204 );
        }
    }

    @Test
    public void testReport()
        throws IOException
    {
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBody( "jms:queue:reports",
            ExchangePattern.InOut, String.format( webhookMessage, Environment.ORG_UNIT_ID ) );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
                "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", "2021W19" ).withParameter( "dataSet", "qNtxTrp56wV" ).transfer()
            .returnAs(
                DataValueSet.class );
        DataValue__1 externalValueDataValue = dataValueSet.getDataValues().get().get( 0 );
        assertEquals( "2", externalValueDataValue.getValue().get() );
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
        throws InterruptedException
    {
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "fields.json" ).then()
            .body( "results.size()", equalTo( 2 ) )
            .body( "results[1].key", equalTo( "dhis2_organisation_unit_id" ) );
    }

    private List<Map<String, Object>> fetchRapidProContacts()
    {
        Map<String, Object> contacts = given( RAPIDPRO_API_REQUEST_SPEC ).get( "/contacts.json" ).then()
            .statusCode( 200 ).extract()
            .body().as(
                Map.class );
        return (List<Map<String, Object>>) contacts.get( "results" );
    }
}
