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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spring.boot.SpringBootCamelContext;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.rapidpro.ProgramStageToFlowMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.apache.camel.builder.Builder.constant;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TriggerRapidProFlowsRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
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
    public void testCreateRapidProContactGivenValidUrn()
        throws
        IOException
    {
        assertRapidProPreCondition();
        camelContext.start();
        Map<String, Object> body = new HashMap<>();
        body.put( "contactUrn", "whatsapp:12345678" );
        body.put( "enrollment", "enrollment-id" );
        producerTemplate.sendBody( "direct:createRapidProContact", body );
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 1 ) )
            .body( "results[0].urns[0]", equalTo( "whatsapp:12345678" ) );
    }

    @Test
    public void testCreateRapidProContactFailsGivenInvalidUrn()
        throws
        Exception
    {
        assertRapidProPreCondition();
        CountDownLatch expectedLogMessage = new CountDownLatch( 1 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "WARN" ) && message.startsWith(
                    "Unexpected status code when creating RapidPro contact for " ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );
        camelContext.start();
        Map<String, Object> body = new HashMap<>();
        body.put( "contactUrn", "whatsapp:invalid" );
        body.put( "enrollment", "enrollment-id" );
        producerTemplate.sendBody( "direct:createRapidProContact", body );
        assertEquals( 0, expectedLogMessage.getCount() );
    }

    @Test
    public void testCreateRapidProContactWhenContactAlreadyExists()
    {
        assertRapidProPreCondition();
        CountDownLatch expectedLogMessage = new CountDownLatch( 2 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "DEBUG" ) && message.startsWith(
                    "RapidPro Contact already exists for DHIS2" ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );
        camelContext.start();
        Map<String, Object> body = new HashMap<>();
        body.put( "contactUrn", "whatsapp:12345678" );
        body.put( "enrollment", "enrollment-id" );
        producerTemplate.sendBody( "direct:createRapidProContact", body );
        assertEquals( 2, expectedLogMessage.getCount() );
        producerTemplate.sendBody( "direct:createRapidProContact", body );
        assertEquals( 1, expectedLogMessage.getCount() );
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 1 ) );
    }

    @Test
    public void testConsumeEventsRoute()
        throws
        Exception
    {
        assertRapidProPreCondition();
        AdviceWith.adviceWith( camelContext, "Consume Events",
            r -> r.weaveByToUri( "direct:triggerRapidProFlow" ).before().to( "mock:spy" ).stop() );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();

        String event = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "event.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBody( "jms:queue:events",
            ExchangePattern.InOnly, event );

        Thread.sleep( 1000 );

        spyEndpoint.assertIsSatisfied();
        Map<String, Object> body = spyEndpoint.getExchanges().get( 0 ).getMessage().getBody( Map.class );
        assertEquals( "event-id", body.get( "event" ) );
        assertEquals( "enrollment-id", body.get( "enrollment" ) );
        assertEquals( "ZP5HZ87wzc0", body.get( "programStage" ) );
        assertEquals( "whatsapp:12345678", body.get( "contactUrn" ) );
        assertEquals( "John", body.get( "givenName" ) );

        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 1 ) )
            .body( "results[0].uuid", equalTo( body.get( "contactUuid" ) ) );
    }

    @Test
    public void checkIfContactHasActiveFlowRunWhenActiveFlow()
        throws
        Exception
    {
        String contactUuid = "contact-uuid";
        String activeFlowRun = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "activeFlowRun.json" ),
            Charset.defaultCharset() );
        AdviceWith.adviceWith( camelContext, "Check If RapidPro Contact Has Active Flow Run", r -> {
            r.weaveById( "flowRunEndpoint" )
                .replace()
                .setBody( constant( activeFlowRun ) );
        } );

        AdviceWith.adviceWith( camelContext, "Check If RapidPro Contact Has Active Flow Run",
            r -> r.weaveByToUri( "direct:savePendingFlow" ).before().to( "mock:spy" ).stop() );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();
        producerTemplate.sendBodyAndProperty( "direct:checkIfContactHasActiveFlowRun", null, "contactUuid",
            contactUuid );
        spyEndpoint.assertIsSatisfied();
    }

    @Test
    public void checkIfContactHasActiveFlowRunWhenNoActiveFlows()
        throws
        Exception
    {
        String contactUuid = "contact-uuid";
        String completedFlowRuns = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "flowRuns.json" ),
            Charset.defaultCharset() );
        AdviceWith.adviceWith( camelContext, "Check If RapidPro Contact Has Active Flow Run", r -> {
            r.weaveById( "flowRunEndpoint" )
                .replace()
                .setBody( constant( completedFlowRuns ) );
        } );

        CountDownLatch expectedLogMessage = new CountDownLatch( 1 );
        ((SpringBootCamelContext) camelContext)
            .addLogListener( ( Exchange exchange, CamelLogger camelLogger, String message ) -> {
                if ( camelLogger.getLevel().name().equals( "DEBUG" ) && message.startsWith(
                    "No active flow runs found for contact =>" ) )
                {
                    expectedLogMessage.countDown();
                }
                return message;
            } );

        camelContext.start();
        producerTemplate.sendBodyAndProperty( "direct:checkIfContactHasActiveFlowRun", null, "contactUuid",
            contactUuid );
        Thread.sleep( 1000 );
        assertEquals( 0, expectedLogMessage.getCount() );
    }

    @Test
    public void testPendingFlowIsAddedToRetryChannel()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Retry Flow",
            r -> r.weaveByToUri( "direct:triggerRapidProFlow" ).before().to( "mock:spy" ).stop() );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();
        String eventFlow = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "event.json" ),
            Charset.defaultCharset() );
        Map<String, Object> eventFlowMap = objectMapper.readValue( eventFlow, Map.class );
        eventFlowMap.put( "contactUuid", "contact-uuid" );
        producerTemplate.sendBodyAndProperty( "direct:savePendingFlow", ExchangePattern.InOut, "originalPayload",
            eventFlowMap );
        eventFlow = objectMapper.writeValueAsString( eventFlowMap );
        spyEndpoint.await( 2, TimeUnit.MINUTES );

        assertEquals( 1, spyEndpoint.getReceivedCounter() );
        List<Map<String, Object>> flowRunChannel = jdbcTemplate.queryForList( "SELECT * FROM FLOW_RUN" );
        assertEquals( 1, flowRunChannel.size() );
        assertEquals( "RETRY", flowRunChannel.get( 0 ).get( "STATUS" ) );
        assertEquals( eventFlow, flowRunChannel.get( 0 ).get( "PAYLOAD" ) );
    }

    private void assertRapidProPreCondition()
    {
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "contacts.json" ).then()
            .body( "results.size()", equalTo( 0 ) );
    }

}
