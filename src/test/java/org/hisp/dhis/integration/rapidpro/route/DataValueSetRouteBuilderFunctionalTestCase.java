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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hisp.dhis.api.model.v2_37_7.DataValueSet;
import org.hisp.dhis.api.model.v2_37_7.DataValue__1;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.rapidpro.SelfSignedHttpClientConfigurer;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DataValueSetRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testDataValueSetIsCreated()
        throws
        IOException
    {
        camelContext.start();
        String contactUuid = syncContactsAndFetchFirstContactUuid();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2",
            ExchangePattern.InOut, String.format( webhookMessage, contactUuid ), Map.of( "dataSetId", "qNtxTrp56wV" ) );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
                "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", PeriodBuilder.yearOf( new Date(), -1 ) ).withParameter( "dataSet", "qNtxTrp56wV" )
            .transfer()
            .returnAs(
                DataValueSet.class );

        DataValue__1 dataValue = dataValueSet.getDataValues().get().get( 0 );
        assertEquals( "2", dataValue.getValue().get() );
        assertTrue( dataValue.getComment().isPresent() );
    }

    @Test
    public void testScheduledReportDelivery()
        throws
        Exception
    {
        System.setProperty( "report.delivery.schedule.expression", "0 0/2 * * * ?" );
        AdviceWith.adviceWith( camelContext, "dhis2Route", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2?exchangePattern=InOnly",
            String.format( webhookMessage, contactUuid ), Map.of( "dataSetId", "qNtxTrp56wV" ) );

        spyEndpoint.await( 1, TimeUnit.MINUTES );
        assertEquals( 0, spyEndpoint.getReceivedCounter() );
        spyEndpoint.await( 1, TimeUnit.MINUTES );
        assertEquals( 1, spyEndpoint.getReceivedCounter() );
    }

    @Test
    public void testReportDestinationEndpoint()
        throws
        Exception
    {
        System.setProperty( "report.destination.endpoint",
            "https://localhost:" + serverPort + "/rapidProConnector/legacy?skipRequestHeaders=true&httpClientConfigurer=#selfSignedHttpClientConfigurer&authenticationPreemptive=true&authMethod=Basic&authUsername=alice&authPassword=secret&httpMethod=POST" );

        camelContext.getRegistry().bind( "selfSignedHttpClientConfigurer", new SelfSignedHttpClientConfigurer() );
        camelContext.addRoutes( new RouteBuilder()
        {
            @Override
            public void configure()
            {
                from( "servlet:legacy?httpMethodRestrict=POST" ).to( "mock:destination" ).removeHeaders( "*" );
            }
        } );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:destination", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.requestBody(
            rapidProConnectorHttpEndpointUri
                + "/webhook?aParam=aValue&dataSetId=qNtxTrp56wV&httpClientConfigurer=#selfSignedHttpClientConfigurer&httpMethod=POST",
            String.format( webhookMessage, contactUuid ), String.class );

        spyEndpoint.await( 5000, TimeUnit.MILLISECONDS );

        assertEquals( 1, spyEndpoint.getReceivedCounter() );
        Map<String, Object> headers = spyEndpoint.getReceivedExchanges().get( 0 ).getMessage().getHeaders();
        assertEquals( "aValue", headers.get( "aParam" ) );
        assertEquals( "Basic YWxpY2U6c2VjcmV0", headers.get( "Authorization" ) );
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

        assertThrows( CamelExecutionException.class, () -> producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2",
            ExchangePattern.InOut, String.format( webhookMessage, UUID.randomUUID() ),
            Map.of( "dataSetId", "qNtxTrp56wV" ) ) );

        List<Map<String, Object>> deadLetterChannel = jdbcTemplate.queryForList( "SELECT * FROM DEAD_LETTER_CHANNEL" );
        assertEquals( 1, deadLetterChannel.size() );
        assertEquals( "ERROR", deadLetterChannel.get( 0 ).get( "STATUS" ) );
        assertEquals( deadLetterChannel.get( 0 ).get( "CREATED_AT" ),
            deadLetterChannel.get( 0 ).get( "LAST_PROCESSED_AT" ) );
        assertEquals( "No results for path: $['results'][0]['fields']['dhis2_organisation_unit_id']",
            deadLetterChannel.get( 0 ).get( "ERROR_MESSAGE" ) );
        Map<String, Object> payload = objectMapper
            .readValue( (String) deadLetterChannel.get( 0 ).get( "PAYLOAD" ), Map.class );
        assertEquals( "John Doe", ((Map<String, Object>) payload.get( "contact" )).get( "name" ) );
    }

    @Test
    public void testRetryRecordInDeadLetterChannelIsReProcessed()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "dhis2Route", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.start();

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );

        String wrongContactUuid = UUID.randomUUID().toString();
        assertThrows( CamelExecutionException.class, () -> producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2",
            ExchangePattern.InOut, String.format( webhookMessage, wrongContactUuid ),
            Map.of( "dataSetId", "qNtxTrp56wV" ) ) );
        assertEquals( 0, spyEndpoint.getReceivedCounter() );

        String payload = (String) jdbcTemplate.queryForList( "SELECT payload FROM DEAD_LETTER_CHANNEL" ).get( 0 )
            .get( "PAYLOAD" );
        jdbcTemplate.execute(
            String.format( "UPDATE DEAD_LETTER_CHANNEL SET STATUS = 'RETRY', PAYLOAD = '%s' WHERE STATUS = 'ERROR'",
                payload.replace( wrongContactUuid, contactUuid ) ) );

        spyEndpoint.await( 10, TimeUnit.SECONDS );

        assertEquals( 1, spyEndpoint.getReceivedCounter() );
        List<Map<String, Object>> deadLetterChannel = jdbcTemplate.queryForList( "SELECT * FROM DEAD_LETTER_CHANNEL" );
        assertEquals( 1, deadLetterChannel.size() );
        assertEquals( "PROCESSED", deadLetterChannel.get( 0 ).get( "STATUS" ) );
        assertTrue( ((OffsetDateTime) deadLetterChannel.get( 0 ).get( "LAST_PROCESSED_AT" )).isAfter(
            (OffsetDateTime) deadLetterChannel.get( 0 ).get( "CREATED_AT" ) ) );
    }

    @Test
    public void testDataValueSetIsCreatedGivenOrgUnitIdSchemeIsCode()
        throws
        IOException
    {
        System.setProperty( "org.unit.id.scheme", "CODE" );
        camelContext.start();

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBodyAndHeaders( "jms:queue:dhis2",
            ExchangePattern.InOut, String.format( webhookMessage, contactUuid ), Map.of( "dataSetId", "qNtxTrp56wV" ) );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
                "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", PeriodBuilder.yearOf( new Date(), -1 ) ).withParameter( "dataSet", "qNtxTrp56wV" )
            .transfer()
            .returnAs(
                DataValueSet.class );

        DataValue__1 dataValue = dataValueSet.getDataValues().get().get( 0 );
        assertEquals( "2", dataValue.getValue().get() );
    }
}
