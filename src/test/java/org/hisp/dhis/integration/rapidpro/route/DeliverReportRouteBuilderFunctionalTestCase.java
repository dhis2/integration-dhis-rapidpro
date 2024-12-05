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

import static org.hisp.dhis.integration.rapidpro.Environment.DHIS_IMAGE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Message;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.hisp.dhis.api.model.v40_0.DataValue;
import org.hisp.dhis.api.model.v40_0.DataValueSet;
import org.hisp.dhis.api.model.v40_0.WebMessage;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.hisp.hieboot.camel.spi.MessageRepository;
import org.hisp.hieboot.camel.spi.RepositoryMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DeliverReportRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    public void testDataValueSetIsCreated()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Report", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();
        String contactUuid = syncContactsAndFetchFirstContactUuid();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2AggregateReports",
            ExchangePattern.InOnly, String.format( webhookMessage, contactUuid ),
            Map.of( "dataSetCode", "MAL_YEARLY" ) );

        spyEndpoint.await( 30, TimeUnit.SECONDS );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
                "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", PeriodBuilder.yearOf( new Date(), -1 ) ).withParameter( "dataSet", "qNtxTrp56wV" )
            .transfer()
            .returnAs(
                DataValueSet.class );

        Optional<DataValue> dataValue = dataValueSet.getDataValues().get().stream()
            .filter( v -> v.getDataElement().get().equals( "tpz77FcntKx" ) ).findFirst();

        assertTrue( dataValue.isPresent() );
        assertEquals( "2", dataValue.get().getValue().get() );
        assertTrue( dataValue.get().getComment().isPresent() );

        Map<String, Object> successLogRow = jdbcTemplate.queryForList( "SELECT * FROM REPORT_SUCCESS_LOG" ).get( 0 );

        String dhisRequest = (String) successLogRow.get( "DHIS_REQUEST" );
        String dhisResponse = (String) successLogRow.get( "DHIS_RESPONSE" );
        String rapidProPayload = (String) successLogRow.get( "RAPIDPRO_PAYLOAD" );

        assertEquals( "MAL_YEARLY", objectMapper.readValue( dhisRequest, Map.class ).get( "dataSet" ) );
        if ( DHIS_IMAGE_NAME.startsWith( "2.36" ) || DHIS_IMAGE_NAME.startsWith( "2.37" ) )
        {
            assertEquals( "SUCCESS", objectMapper.readValue( dhisResponse, Map.class ).get( "status" ) );
        }
        else
        {
            assertEquals( "OK", objectMapper.readValue( dhisResponse, Map.class ).get( "status" ) );
        }
        assertEquals( "John Doe",
            ((Map) objectMapper.readValue( rapidProPayload, Map.class ).get( "contact" )).get( "name" ) );
    }

    @Test
    public void testRecordInDeadLetterChannelIsCreatedGivenWebMessageErrorWhileCreatingDataValueSet()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Report",
            r -> r.weaveByToUri( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" )
                .replace().to( "mock:dhis2" ) );
        MockEndpoint fakeDhis2Endpoint = camelContext.getEndpoint( "mock:dhis2", MockEndpoint.class );
        fakeDhis2Endpoint.whenAnyExchangeReceived(
            exchange -> exchange.getMessage().setBody( objectMapper.writeValueAsString(
                new WebMessage().withStatus( WebMessage.Status.ERROR ) ) ) );

        camelContext.start();
        String contactUuid = syncContactsAndFetchFirstContactUuid();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2AggregateReports",
            ExchangePattern.InOut, String.format( webhookMessage, contactUuid ),
            Map.of( "dataSetCode", "MAL_YEARLY", "orgUnitId", "acme" ) );

        List<RepositoryMessage> repositoryMessages = messageRepository.retrieve( "failed:*" );
        assertEquals( 1, repositoryMessages.size() );
        assertEquals(
            "Import error from DHIS2 while saving data value set => {code=null, devMessage=null, httpStatus=null, httpStatusCode=null, message=null, response=null, status=ERROR}",
            repositoryMessages.get( 0 ).getContext() );
    }

    @Test
    public void testRecordInDeadLetterChannelIsCreatedGivenMissingDataSetCode()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Report",
            r -> r.weaveByToUri( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" )
                .replace().to( "mock:dhis2" ) );
        MockEndpoint fakeDhis2Endpoint = camelContext.getEndpoint( "mock:dhis2", MockEndpoint.class );
        fakeDhis2Endpoint.whenAnyExchangeReceived(
            exchange -> exchange.getMessage().setBody( objectMapper.writeValueAsString(
                new WebMessage().withStatus( WebMessage.Status.ERROR ) ) ) );

        camelContext.start();
        String contactUuid = syncContactsAndFetchFirstContactUuid();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );

        assertThrows( CamelExecutionException.class,
            () -> producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2AggregateReports",
                ExchangePattern.InOut, String.format( webhookMessage, contactUuid ),
                Map.of( "orgUnitId", Environment.ORG_UNIT_ID ) ) );

        List<RepositoryMessage> repositoryMessages = messageRepository.retrieve( "failed:*" );
        assertEquals( 1, repositoryMessages.size() );
        Map<String, Object> payload = objectMapper.readValue(
            (String) repositoryMessages.get( 0 ).getMessage().getBody(),
            Map.class );
        assertNull( payload.get( "data_set_code" ) );
    }

    @Test
    @Timeout( value = 5, unit = TimeUnit.MINUTES )
    public void testScheduledReportDelivery()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        System.setProperty( "report.delivery.schedule.expression", "0 0/1 * * * ?" );
        AdviceWith.adviceWith( camelContext, "Transmit Report", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();
        camelContext.getRouteController().stopRoute( "Schedule Report Delivery" );

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2AggregateReports?exchangePattern=InOnly",
            String.format( webhookMessage, contactUuid ), Map.of( "dataSetCode", "MAL_YEARLY" ) );

        spyEndpoint.await( 30, TimeUnit.SECONDS );
        assertEquals( 0, spyEndpoint.getReceivedCounter() );

        camelContext.getRouteController().startRoute( "Schedule Report Delivery" );

        spyEndpoint.await();
        assertEquals( 1, spyEndpoint.getReceivedCounter() );
    }

    @Test
    public void testRecordInDeadLetterChannelIsCreatedGivenErrorWhileCreatingDataValueSet()
        throws
        IOException
    {
        camelContext.start();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );

        assertThrows( CamelExecutionException.class,
            () -> producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2AggregateReports", ExchangePattern.InOut,
                String.format( webhookMessage, UUID.randomUUID() ), Map.of( "dataSetCode", "MAL_YEARLY" ) ) );

        List<RepositoryMessage> repositoryMessages = messageRepository.retrieve( "failed:*" );
        assertEquals( 1, repositoryMessages.size() );
        RepositoryMessage repositoryMessage = repositoryMessages.get( 0 );
        Map<String, Object> payload = objectMapper.readValue( (String) repositoryMessage.getMessage().getBody(),
            Map.class );
        assertTrue( repositoryMessage.getContext().startsWith(
            "org.apache.camel.CamelExchangeException: Error occurred during aggregation." ) );
        assertEquals( "John Doe", ((Map<String, Object>) payload.get( "contact" )).get( "name" ) );
    }

    @Test
    public void testRetryRecordInDeadLetterChannelIsReProcessed()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Report", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );

        String wrongContactUuid = UUID.randomUUID().toString();
        assertThrows( CamelExecutionException.class,
            () -> producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2AggregateReports", ExchangePattern.InOut,
                String.format( webhookMessage, wrongContactUuid ), Map.of( "dataSetCode", "MAL_YEARLY" ) ) );

        assertEquals( 0, spyEndpoint.getReceivedCounter() );

        List<RepositoryMessage> delete = messageRepository.delete( "*" );
        delete.get( 0 ).getMessage()
            .setBody( ((String) delete.get( 0 ).getMessage().getBody()).replace( wrongContactUuid, contactUuid ) );
        messageRepository.store( delete.get( 0 ).getKey().replace( "failed:", "replay:" ),
            delete.get( 0 ).getMessage() );

        spyEndpoint.await( 1, TimeUnit.MINUTES );

        assertEquals( 1, spyEndpoint.getReceivedCounter() );
    }

    @Test
    public void testDataValueSetIsCreatedGivenOrgUnitIdSchemeIsCode()
        throws
        IOException
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        System.setProperty( "org.unit.id.scheme", "CODE" );
        camelContext.start();

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2AggregateReports",
            ExchangePattern.InOut, String.format( webhookMessage, contactUuid ),
            Map.of( "dataSetCode", "MAL_YEARLY" ) );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
                "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", PeriodBuilder.yearOf( new Date(), -1 ) ).withParameter( "dataSet", "qNtxTrp56wV" )
            .transfer()
            .returnAs(
                DataValueSet.class );

        Optional<DataValue> dataValue = dataValueSet.getDataValues().get().stream()
            .filter( v -> v.getDataElement().get().equals( "tpz77FcntKx" ) ).findFirst();

        assertTrue( dataValue.isPresent() );
        assertEquals( "2", dataValue.get().getValue().get() );
        assertTrue( dataValue.get().getComment().isPresent() );
    }
}