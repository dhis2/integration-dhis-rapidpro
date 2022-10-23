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

import io.restassured.http.ContentType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.hisp.dhis.api.model.v2_37_7.DataValueSet;
import org.hisp.dhis.api.model.v2_37_7.DataValue__1;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PullReportsRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    private String flowUuid;

    @Override
    public void doBeforeEach()
    {
        flowUuid = given( RAPIDPRO_API_REQUEST_SPEC ).get( "flows.json" ).then().extract()
            .path( "results[0].uuid" );
        System.setProperty( "rapidpro.flow.uuids", flowUuid );
    }

    @Test
    public void testPullGivenNoPriorFlowRun()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Deliver Report", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );

        camelContext.start();
        syncContactsAndFetchFirstContactUuid();

        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );
        Thread.sleep( 15000 );
        assertEquals( 0, spyEndpoint.getReceivedCounter() );
    }

    @Test
    public void testPullGivenPriorFlowRun()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Deliver Report", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );

        camelContext.start();
        syncContactsAndFetchFirstContactUuid();

        runFlowAndWaitUntilCompleted( flowUuid );
        spyEndpoint.setExpectedCount( 1 );
        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );

        spyEndpoint.await( 15, TimeUnit.SECONDS );
        assertEquals( 1, spyEndpoint.getReceivedCounter() );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
                "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", PeriodBuilder.yearOf( new Date(), -1 ) ).withParameter( "dataSet", "qNtxTrp56wV" )
            .transfer()
            .returnAs(
                DataValueSet.class );

        assertEquals("5", dataValueSet.getDataValues().get().get( 0 ).getValue().get());
        assertEquals("2", dataValueSet.getDataValues().get().get( 1 ).getValue().get());
        assertEquals("3", dataValueSet.getDataValues().get().get( 2 ).getValue().get());
        assertEquals("10", dataValueSet.getDataValues().get().get( 3 ).getValue().get());
        assertEquals("rtfSaMjPyq6", dataValueSet.getDataValues().get().get( 3 ).getCategoryOptionCombo().get());
    }

    @Test
    public void testConsecutivePullsWithInterleavingFlowRuns()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Deliver Report", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );

        camelContext.start();
        syncContactsAndFetchFirstContactUuid();

        runFlowAndWaitUntilCompleted( flowUuid );
        runFlowAndWaitUntilCompleted( flowUuid );
        runFlowAndWaitUntilCompleted( flowUuid );

        spyEndpoint.setExpectedCount( 3 );
        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );
        spyEndpoint.await( 15, TimeUnit.SECONDS );
        assertEquals( 3, spyEndpoint.getReceivedCounter() );
    }

    @Test
    public void testPullGivenNextPage()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Scan RapidPro Flows",
            r -> r.weaveByToUri( "${exchangeProperty.nextRunsPageUrl}" ).replace().to( "mock:rapidPro" ) );
        MockEndpoint rapidProMockEndpoint = camelContext.getEndpoint( "mock:rapidPro", MockEndpoint.class );
        rapidProMockEndpoint.whenAnyExchangeReceived( exchange -> {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> flowRuns = objectMapper.readValue(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(
                    "flowRuns.json" ), Map.class );

            List<Map<String, Object>> results = (List<Map<String, Object>>) flowRuns.get( "results" );
            Map<String, Object> result = results.get( 0 );
            for ( int i = 0; i < 249; i++ )
            {
                results.add( result );
            }

            if ( !exchange.getProperties().get( "nextRunsPageUrl" ).equals( "mock:rapidPro?page=2" ) )
            {
                flowRuns.put( "next", "mock:rapidPro?page=2" );
            }
            exchange.getMessage().setBody( objectMapper.writeValueAsString( flowRuns ) );
        } );

        AdviceWith.adviceWith( camelContext, "Scan RapidPro Flows",
            r -> r.weaveByToUri( "jms:queue:dhis2?exchangePattern=InOnly" ).replace().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );

        camelContext.start();
        syncContactsAndFetchFirstContactUuid();

        spyEndpoint.setExpectedCount( 500 );
        producerTemplate.sendBody( "direct:pull", null );
        spyEndpoint.await( 30, TimeUnit.SECONDS );
        assertEquals( 500, spyEndpoint.getReceivedCounter() );
    }

    private void runFlow( String flowUuid )
    {
        given( RAPIDPRO_API_REQUEST_SPEC ).contentType( ContentType.JSON ).body(
                Map.of( "flow", flowUuid, "urns", List.of( "tel:0035621000001" ) ) ).when().post( "flow_starts.json" )
            .then();
    }

    private void runFlowAndWaitUntilCompleted( String flowUuid )
        throws
        InterruptedException
    {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS" );
        simpleDateFormat.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
        Date now = new Date();
        runFlow( flowUuid );
        waitUntilFlowRunIsCompleted( flowUuid, now );
    }

    private void waitUntilFlowRunIsCompleted( String flowUuid, Date after )
        throws
        InterruptedException
    {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS" );
        simpleDateFormat.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
        String afterAsString = simpleDateFormat.format( after );

        String exitType = "";
        while ( exitType == null || !exitType.equals( "completed" ) )
        {
            exitType = given( RAPIDPRO_API_REQUEST_SPEC )
                .queryParam( "flow", flowUuid ).queryParam( "after", afterAsString ).when()
                .get( "runs.json" ).then().statusCode( 200 ).extract().path( "results[0].exit_type" );
            Thread.sleep( 1000 );
        }
    }
}
