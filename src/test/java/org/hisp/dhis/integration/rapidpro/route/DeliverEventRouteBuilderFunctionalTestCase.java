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
import org.hisp.dhis.api.model.v40_0.DataValue;
import org.hisp.dhis.api.model.v40_0.DataValueSet;
import org.hisp.dhis.api.model.v40_0.Dxf2EventsEventDataValue;
import org.hisp.dhis.api.model.v40_0.Event;
import org.hisp.dhis.api.model.v40_0.WebMessage;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hisp.dhis.integration.rapidpro.Environment.DHIS_IMAGE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeliverEventRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testEventIsUpdated()
        throws
        Exception
    {
        System.setProperty( "sync.dhis2.events.to.rapidpro.flows", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Event", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        String eventId = createTrackedEntityAndFetchEventId( "12345678" );
        syncTrackedEntityContact( "whatsapp:12345678" );
        camelContext.start();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "eventWebhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2ProgramStageEvents",
            ExchangePattern.InOnly, String.format( webhookMessage, eventId ),
            Map.of( "eventId", eventId ) );

        spyEndpoint.await( 30, TimeUnit.SECONDS );

        Event event = Environment.DHIS2_CLIENT.get(
                "events/{eventId}", eventId ).withFields( "event", "status", "datavalues" )
            .transfer()
            .returnAs(
                Event.class );

        Optional<Dxf2EventsEventDataValue> dataValue = event.getDataValues().get().stream()
            .filter( v -> v.getDataElement().get().equals( "at60cTl6IdS" ) ).findFirst();
        assertTrue( dataValue.isPresent() );
        assertEquals( "12:00", dataValue.get().getValue().get() );

        Map<String, Object> eventSuccessLog = jdbcTemplate.queryForList( "SELECT * FROM EVENT_SUCCESS_LOG" ).get( 0 );

        String dhisRequest = (String) eventSuccessLog.get( "DHIS_REQUEST" );
        String dhisResponse = (String) eventSuccessLog.get( "DHIS_RESPONSE" );
        String rapidProPayload = (String) eventSuccessLog.get( "RAPIDPRO_PAYLOAD" );
        String successEventLogEventId = (String) eventSuccessLog.get( "EVENT_ID" );
        List<Map<String, Object>> events = (List<Map<String, Object>>) objectMapper.readValue( dhisRequest, Map.class )
            .get( "events" );
        assertEquals( eventId, events.get( 0 ).get( "event" ) );
        assertEquals( eventId, successEventLogEventId );
        assertEquals( "COMPLETED", events.get( 0 ).get( "status" ) );
        if ( DHIS_IMAGE_NAME.startsWith( "2.36" ) || DHIS_IMAGE_NAME.startsWith( "2.37" ) )
        {
            assertEquals( "SUCCESS", objectMapper.readValue( dhisResponse, Map.class ).get( "status" ) );
        }
        else
        {
            assertEquals( "OK", objectMapper.readValue( dhisResponse, Map.class ).get( "status" ) );
        }
        assertEquals( "whatsapp:12345678",
            ((Map) objectMapper.readValue( rapidProPayload, Map.class ).get( "contact" )).get( "urn" ) );
    }

    @Test
    public void testRecordInFailedEventDeliveryIsCreatedGivenWebMessageErrorWhileUpdatingEvent()
        throws
        Exception
    {
        System.setProperty( "sync.dhis2.events.to.rapidpro.flows", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Event",
            r -> r.weaveByToUri( "dhis2://post/resource?path=tracker&inBody=resource&client=#dhis2Client" )
                .replace().to( "mock:dhis2" ) );
        MockEndpoint fakeDhis2Endpoint = camelContext.getEndpoint( "mock:dhis2", MockEndpoint.class );
        fakeDhis2Endpoint.whenAnyExchangeReceived(
            exchange -> exchange.getMessage().setBody( objectMapper.writeValueAsString(
                new WebMessage().withStatus( WebMessage.Status.ERROR ) ) ) );
        String eventId = createTrackedEntityAndFetchEventId( "12345678" );
        syncTrackedEntityContact( "whatsapp:12345678" );
        camelContext.start();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "eventWebhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2ProgramStageEvents",
            ExchangePattern.InOut, String.format( webhookMessage, eventId ),
            Map.of( "eventId", eventId ) );
        List<Map<String, Object>> failedEventDelivery = jdbcTemplate.queryForList(
            "SELECT * FROM EVENT_DEAD_LETTER_CHANNEL" );
        assertEquals( 1, failedEventDelivery.size() );
        assertEquals( "ERROR",
            objectMapper.readValue( (String) failedEventDelivery.get( 0 ).get( "error_message" ),
                    WebMessage.class )
                .getStatus().value() );
    }

    @Test
    public void testRecordInFailedEventDeliveryIsCreatedGivenInvalidEventId()
        throws
        Exception
    {
        System.setProperty( "sync.dhis2.events.to.rapidpro.flows", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Event", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        String eventId = createTrackedEntityAndFetchEventId( "12345678" );
        syncTrackedEntityContact( "whatsapp:12345678" );
        camelContext.start();

        String invalidId = "trigger.params.eventId";
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "eventWebhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2ProgramStageEvents",
            ExchangePattern.InOut, String.format( webhookMessage, invalidId ),
            Map.of( "eventId", invalidId ) );
        spyEndpoint.await( 30, TimeUnit.SECONDS );
        List<Map<String, Object>> failedEventDelivery = jdbcTemplate.queryForList(
            "SELECT * FROM EVENT_DEAD_LETTER_CHANNEL" );
        assertEquals( 1, failedEventDelivery.size() );
        assertEquals( invalidId, failedEventDelivery.get( 0 ).get( "event_id" ) );
    }

    @Test
    public void testRetryRecordInFailedEventDeliveryIsReProcessed()
        throws
        Exception
    {
        System.setProperty( "sync.dhis2.events.to.rapidpro.flows", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Event", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );
        String eventId = createTrackedEntityAndFetchEventId( "12345678" );
        syncTrackedEntityContact( "whatsapp:12345678" );
        camelContext.start();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "eventWebhook.json" ),
            Charset.defaultCharset() );

        String invalidEventId = UUID.randomUUID().toString();
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2ProgramStageEvents", ExchangePattern.InOut,
            String.format( webhookMessage, invalidEventId ), Map.of( "eventId", invalidEventId ) );
        assertEquals( 0, spyEndpoint.getReceivedCounter() );

        String payload = (String) jdbcTemplate.queryForList( "SELECT event_id FROM EVENT_DEAD_LETTER_CHANNEL" ).get( 0 )
            .get( "EVENT_ID" );
        jdbcTemplate.execute(
            String.format(
                "UPDATE EVENT_DEAD_LETTER_CHANNEL SET STATUS = 'RETRY', EVENT_ID = '%s' WHERE STATUS = 'ERROR'",
                payload.replace( invalidEventId, eventId ) ) );

        spyEndpoint.await( 30, TimeUnit.SECONDS );

        assertEquals( 1, spyEndpoint.getReceivedCounter() );
        List<Map<String, Object>> failedEventDelivery = jdbcTemplate.queryForList(
            "SELECT * FROM EVENT_DEAD_LETTER_CHANNEL" );
        assertEquals( 1, failedEventDelivery.size() );
        assertEquals( "PROCESSED", failedEventDelivery.get( 0 ).get( "STATUS" ) );

        Object lastProcessedAt = failedEventDelivery.get( 0 ).get( "LAST_PROCESSED_AT" );
        Instant lastProcessedAsInstant;
        if ( lastProcessedAt instanceof OffsetDateTime )
        {
            lastProcessedAsInstant = ((OffsetDateTime) lastProcessedAt).toInstant();
        }
        else
        {
            lastProcessedAsInstant = ((Timestamp) lastProcessedAt).toInstant();
        }

        Object createdAt = failedEventDelivery.get( 0 ).get( "CREATED_AT" );
        Instant createdAtAsInstant;
        if ( createdAt instanceof OffsetDateTime )
        {
            createdAtAsInstant = ((OffsetDateTime) createdAt).toInstant();
        }
        else
        {
            createdAtAsInstant = ((Timestamp) createdAt).toInstant();
        }

        assertTrue( lastProcessedAsInstant.isAfter( createdAtAsInstant ) );
    }

}
