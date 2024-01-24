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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spring.boot.SpringBootCamelContext;
import org.hisp.dhis.api.model.v40_0.TrackedEntity;
import org.hisp.dhis.api.model.v40_0.WebapiControllerTrackerViewAttribute;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.rapidpro.ProgramStageToFlowMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.hisp.dhis.integration.rapidpro.Environment.DHIS2_CLIENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueueProgramStageEventsRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Autowired
    protected ProgramStageToFlowMap programStageToFlowMap;

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public void doBeforeEach()
        throws
        IOException,
        ParseException
    {
        System.setProperty( "dhis2.phone.number.attribute.code", "PHONE_LOCAL" );
        System.setProperty( "dhis2.given.name.attribute.code", "GIVEN_NAME" );
        System.setProperty( "sync.dhis2.events.to.rapidpro.flows", "true" );
        programStageToFlowMap.add( "ZP5HZ87wzc0", "specimen-collection-flow-uuid-placeholder" );
        programStageToFlowMap.add( "Ish2wk3eLg3", "laboratory-testing-flow-uuid-placeholder" );
    }

    @Override
    public void doAfterEach()
        throws
        Exception
    {
        programStageToFlowMap.clear();
        Environment.deleteDhis2TrackedEntities( Environment.ORG_UNIT_ID );
    }

    @Test
    public void testDueEventsCountWithSingleProgramStage()
        throws
        Exception
    {
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 10, List.of( "ZP5HZ87wzc0" ) );
        AdviceWith.adviceWith( camelContext, "Fetch Due Events", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.expectedMessageCount( 1 );
        camelContext.start();
        producerTemplate.sendBody( "direct:fetchDueEvents", ExchangePattern.InOnly, null );
        spyEndpoint.assertIsSatisfied( 5000 );
        Exchange exchange = spyEndpoint.getExchanges().get( 0 );
        int dueEventsCount = exchange.getProperty( "dueEventsCount", Integer.class );
        assertEquals( 10, dueEventsCount );
    }

    @Test
    public void testDueEventsCountWithMultipleProgramStages()
        throws
        Exception
    {
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 2,
            List.of( "ZP5HZ87wzc0" ) );
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 3,
            List.of( "Ish2wk3eLg3", "ZP5HZ87wzc0" ) );
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 2,
            List.of( "Ish2wk3eLg3" ) );
        AdviceWith.adviceWith( camelContext, "Fetch Due Events", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.expectedMessageCount( 1 );
        camelContext.start();

        producerTemplate.sendBody( "direct:fetchDueEvents", ExchangePattern.InOnly, null );
        spyEndpoint.assertIsSatisfied( 10000 );
        Exchange exchange = spyEndpoint.getExchanges().get( 0 );
        int dueEventsCount = exchange.getProperty( "dueEventsCount", Integer.class );
        assertEquals( 10, dueEventsCount );
    }

    @Test
    public void testDueEventsCountWithZeroProgramStages()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Fetch Due Events", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.expectedMessageCount( 1 );
        camelContext.start();

        producerTemplate.sendBody( "direct:fetchDueEvents", ExchangePattern.InOnly, null );
        spyEndpoint.assertIsSatisfied( 10000 );
        Exchange exchange = spyEndpoint.getExchanges().get( 0 );
        int dueEventsCount = exchange.getProperty( "dueEventsCount", Integer.class );
        assertEquals( 0, dueEventsCount );
    }

    @Test
    public void testDueEventsCountUnderLoad()
        throws
        Exception
    {
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 50, List.of( "ZP5HZ87wzc0" ) );
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 50, List.of( "Ish2wk3eLg3" ) );
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 250,
            List.of( "Ish2wk3eLg3", "ZP5HZ87wzc0" ) );
        AdviceWith.adviceWith( camelContext, "Fetch Due Events", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.expectedMessageCount( 1 );
        camelContext.start();

        producerTemplate.sendBody( "direct:fetchDueEvents", ExchangePattern.InOnly, null );
        spyEndpoint.assertIsSatisfied( 60000 );
        Exchange exchange = spyEndpoint.getExchanges().get( 0 );
        int dueEventsCount = exchange.getProperty( "dueEventsCount", Integer.class );
        assertEquals( 600, dueEventsCount );
    }

    @Test
    public void testFetchTrackedEntityId()
        throws
        Exception
    {
        String enrollmentId = Environment.createDhis2TrackedEntityWithEnrollment( Environment.ORG_UNIT_ID, "1234",
            "ID-1234", "John", List.of( "ZP5HZ87wzc0" ) );
        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        camelContext.start();

        Map<String, Object> body = new HashMap<>();
        body.put( "enrollment", enrollmentId );
        producerTemplate.sendBody( "direct:fetchAttributes", ExchangePattern.InOut, body );
        spyEndpoint.assertIsSatisfied();
        String trackedEntityId = (String) DHIS2_CLIENT.get( "tracker/enrollments/{}", enrollmentId ).transfer()
            .returnAs(
                Map.class ).get( "trackedEntity" );
        Map<String, Object> bodyAfterAttributeEnrichment = spyEndpoint.getExchanges().get( 0 ).getMessage()
            .getBody( Map.class );
        assertEquals( trackedEntityId, bodyAfterAttributeEnrichment.get( "trackedEntity" ) );
    }

    @Test
    public void testAttributesEndpointWhenProgramAttributes()
        throws
        Exception
    {
        String enrollmentId = Environment.createDhis2TrackedEntityWithEnrollment( Environment.ORG_UNIT_ID, "1234",
            "ID-12345", "John", List.of( "ZP5HZ87wzc0" ) );
        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        camelContext.start();

        Map<String, Object> body = new HashMap<>();
        body.put( "enrollment", enrollmentId );
        producerTemplate.sendBody( "direct:fetchAttributes", ExchangePattern.InOut, body );
        spyEndpoint.assertIsSatisfied();
        Exchange exchange = spyEndpoint.getExchanges().get( 0 );
        String attributeEndpoint = (String) exchange.getProperty( "attributesEndpoint" );
        assertEquals(
            "dhis2://get/resource?path=tracker/enrollments/" + enrollmentId + "&fields=attributes[attribute,code,value]&client=#dhis2Client",
            attributeEndpoint );
    }

    @Test
    public void testAttributesEndpointWhenTypeAttributes()
        throws
        Exception
    {
        String enrollmentId = Environment.createDhis2TrackedEntityWithEnrollment( Environment.ORG_UNIT_ID, "1234",
            "ID-123456", "John", List.of( "ZP5HZ87wzc0" ) );
        String trackedEntityId = (String) DHIS2_CLIENT.get( "tracker/enrollments/{}", enrollmentId ).transfer()
            .returnAs(
                Map.class ).get( "trackedEntity" );

        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> {
            r.interceptSendToEndpoint(
                    "dhis2://get/resource?path=tracker/enrollments/" + enrollmentId + "&fields=trackedEntity,attributes[code]&client=#dhis2Client" )
                .skipSendToOriginalEndpoint()
                .setBody( exchange -> "{\"trackedEntity\": \"" + trackedEntityId + "\", \"attributes\": [] }" );
        } );
        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        camelContext.start();

        Map<String, Object> body = new HashMap<>();
        body.put( "contactUrn", "1234" );
        body.put( "enrollment", enrollmentId );
        producerTemplate.sendBody( "direct:fetchAttributes", ExchangePattern.InOut, body );
        spyEndpoint.assertIsSatisfied();
        Exchange exchange = spyEndpoint.getExchanges().get( 0 );
        String attributeEndpoint = (String) exchange.getProperty( "attributesEndpoint" );

        assertEquals(
            "dhis2://get/resource?path=tracker/trackedEntities/" + trackedEntityId + "&fields=attributes[attribute,code,value]&client=#dhis2Client",
            attributeEndpoint );
    }

    @Test
    public void testFetchAttributesWhenTrackedEntityProgramAttributes()
        throws
        Exception
    {
        String enrollmentId = Environment.createDhis2TrackedEntityWithEnrollment( Environment.ORG_UNIT_ID, "12345678",
            "ID-1234567", "John", List.of( "ZP5HZ87wzc0" ) );
        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        camelContext.start();

        Map<String, Object> body = new HashMap<>();
        body.put( "enrollment", enrollmentId );
        producerTemplate.sendBody( "direct:fetchAttributes", ExchangePattern.InOut, body );
        spyEndpoint.assertIsSatisfied();
        Map<String, Object> bodyAfterAttributeEnrichment = spyEndpoint.getExchanges().get( 0 ).getMessage()
            .getBody( Map.class );
        String expectedContactUrn = "whatsapp:12345678";
        String expectedGivenName = "John";
        assertEquals( expectedContactUrn, bodyAfterAttributeEnrichment.get( "contactUrn" ) );
        assertEquals( expectedGivenName, bodyAfterAttributeEnrichment.get( "givenName" ) );
    }

    @Test
    public void testFetchAttributesWhenTrackedEntityTypeAttributes()
        throws
        Exception
    {
        String enrollmentId = Environment.createDhis2TrackedEntityWithEnrollment( Environment.ORG_UNIT_ID, "12345678",
            "ID-12", "John", List.of( "ZP5HZ87wzc0" ) );
        String trackedEntityId = (String) DHIS2_CLIENT.get( "tracker/enrollments/{}", enrollmentId ).transfer()
            .returnAs(
                Map.class ).get( "trackedEntity" );

        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> {
            r.interceptSendToEndpoint(
                    "dhis2://get/resource?path=tracker/enrollments/" + enrollmentId + "&fields=trackedEntity,attributes[code]&client=#dhis2Client" )
                .skipSendToOriginalEndpoint()
                .setBody( exchange -> "{\"trackedEntity\": \"" + trackedEntityId + "\", \"attributes\": [] }" );
        } );

        TrackedEntity trackedEntity = new TrackedEntity()
            .withTrackedEntity( trackedEntityId )
            .withAttributes( List.of(
                new WebapiControllerTrackerViewAttribute().withAttribute( "sB1IHYu2xQT" ).withCode( "GIVEN_NAME" )
                    .withValue( "John" ),
                new WebapiControllerTrackerViewAttribute().withAttribute( "fctSQp5nAYl" ).withCode( "PHONE_LOCAL" )
                    .withValue( "12345678" )
            ) )
            .withOrgUnit( Environment.ORG_UNIT_ID );

        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> {
            r.interceptSendToEndpoint(
                    "dhis2://get/resource?path=tracker/trackedEntities/" + trackedEntityId + "&fields=attributes[attribute,code,value]&client=#dhis2Client" )
                .skipSendToOriginalEndpoint()
                .setBody(
                    exchange -> {
                        try
                        {
                            return objectMapper.writeValueAsString( trackedEntity );
                        }
                        catch ( JsonProcessingException e )
                        {
                            throw new RuntimeException( e );
                        }
                    } );
        } );
        AdviceWith.adviceWith( camelContext, "Fetch Attributes", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        camelContext.start();

        Map<String, Object> body = new HashMap<>();
        body.put( "enrollment", enrollmentId );
        producerTemplate.sendBody( "direct:fetchAttributes", ExchangePattern.InOut, body );
        spyEndpoint.assertIsSatisfied();
        Map<String, Object> bodyAfterAttributeEnrichment = spyEndpoint.getExchanges().get( 0 ).getMessage()
            .getBody( Map.class );
        String expectedContactUrn = "whatsapp:12345678";
        String expectedGivenName = "John";
        assertEquals( expectedContactUrn, bodyAfterAttributeEnrichment.get( "contactUrn" ) );
        assertEquals( expectedGivenName, bodyAfterAttributeEnrichment.get( "givenName" ) );
    }

    @Test
    public void testFetchAttributesLogsErrorWhenInvalidPhoneNumberCode()
        throws
        Exception
    {
        System.setProperty( "dhis2.phone.number.attribute.code", "invalid" );
        String enrollmentId = Environment.createDhis2TrackedEntityWithEnrollment( Environment.ORG_UNIT_ID, "12345678",
            "ID-1234567", "John", List.of( "ZP5HZ87wzc0" ) );
        CountDownLatch expectedLogMessage = new CountDownLatch( 1 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "ERROR" ) && message.startsWith(
                    "Error while fetching phone number attribute from DHIS2 enrollment" ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );
        camelContext.start();

        Map<String, Object> body = new HashMap<>();
        body.put( "enrollment", enrollmentId );
        producerTemplate.sendBody( "direct:fetchAttributes", ExchangePattern.InOut, body );
        Thread.sleep( 2000 );
        assertEquals( 0, expectedLogMessage.getCount() );
    }

    @Test
    public void testEventTransformationReturnsExpectedValues()
        throws
        Exception
    {
        String phoneNumber = "12345678";
        String givenName = "John";
        String programStage = "ZP5HZ87wzc0";
        String enrollmentId = Environment.createDhis2TrackedEntityWithEnrollment( Environment.ORG_UNIT_ID, phoneNumber,
            "ID-1", givenName, List.of( programStage ) );
        AdviceWith.adviceWith( camelContext, "Queue Program Stage Events",
            r -> r.interceptSendToEndpoint( "jms:queue:events?exchangePattern=InOnly" ).to( "mock:spy" ).stop() );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();
        producerTemplate.sendBody( "direct:queueEvents", ExchangePattern.InOut, null );
        spyEndpoint.assertIsSatisfied( 10000 );
        Exchange exchange = spyEndpoint.getExchanges().get( 0 );
        Map<String, Object> body = objectMapper.readValue( exchange.getMessage().getBody( String.class ), Map.class );
        String expectedContactUrn = "whatsapp:" + phoneNumber;
        assertEquals( expectedContactUrn, body.get( "contactUrn" ) );
        assertEquals( givenName, body.get( "givenName" ) );
        assertEquals( enrollmentId, body.get( "enrollment" ) );
        assertEquals( programStage, body.get( "programStage" ) );
    }

    @Test
    public void testUpdateDhisProgramStageEventStatusWithValidEventId()
        throws
        Exception
    {
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 1, List.of( "ZP5HZ87wzc0" ) );
        Thread.sleep( 1000 );
        Map<String, Object> payload = (Map<String, Object>) DHIS2_CLIENT.get( "tracker/events" )
            .transfer()
            .returnAs( Map.class );
        List<Map<String, Object>> instances = (List<Map<String, Object>>) payload.get( "instances" );
        Map<String, Object> event = instances.get( 0 );
        CountDownLatch expectedLogMessage = new CountDownLatch( 1 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "DEBUG" ) && message.startsWith(
                    String.format( "Successfully updated DHIS program stage event status for event with ID => %s",
                        event.get( "event" ) ) ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );

        camelContext.start();
        producerTemplate.sendBodyAndProperty( "direct:updateDhisProgramStageEventStatus", null, "eventPayload", event );
        Thread.sleep( 1000 );
        assertEquals( 0, expectedLogMessage.getCount() );
    }

    @Test
    public void testQueueEvents()
        throws
        Exception
    {
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 10, List.of( "ZP5HZ87wzc0" ) );
        CountDownLatch expectedLogMessage = new CountDownLatch( 10 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "DEBUG" ) && message.startsWith(
                    "Successfully updated DHIS program stage event status for event with ID =>" ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );

        AdviceWith.adviceWith( camelContext, "Queue Program Stage Events",
            r -> r.interceptSendToEndpoint( "jms:queue:events?exchangePattern=InOnly" ).to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 10 );

        camelContext.start();
        producerTemplate.sendBody( "direct:queueEvents", ExchangePattern.InOut, null );
        spyEndpoint.assertIsSatisfied( 10000 );
        assertEquals( 0, expectedLogMessage.getCount() );
    }

}
