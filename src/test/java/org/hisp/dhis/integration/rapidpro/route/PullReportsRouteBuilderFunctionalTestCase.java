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
import io.restassured.http.ContentType;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.TransformDefinition;
import org.hisp.dhis.api.model.v2_38_1.DataValueSet;
import org.hisp.dhis.api.model.v2_38_1.DataValue__1;
import org.hisp.dhis.api.model.v2_38_1.DescriptiveWebMessage;
import org.hisp.dhis.api.model.v2_38_1.WebMessage;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PullReportsRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Autowired
    private ObjectMapper objectMapper;

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
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Report", r -> r.weaveAddLast().to( "mock:spy" ) );
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
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Report", r -> r.weaveAddLast().to( "mock:spy" ) );
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

        assertEquals( "5", getDataValue( "EpyvZBsqMmM", dataValueSet ).getValue().get() );
        assertEquals( "2", getDataValue( "tpz77FcntKx", dataValueSet ).getValue().get() );
        assertEquals( "3", getDataValue( "UH47dKFqTRK", dataValueSet ).getValue().get() );
        assertEquals( "10", getDataValue( "dFaBg0HpoIL", dataValueSet ).getValue().get() );
        assertEquals( "rtfSaMjPyq6", getDataValue( "dFaBg0HpoIL", dataValueSet ).getCategoryOptionCombo().get() );
    }

    private DataValue__1 getDataValue( String dataElementId, DataValueSet dataValueSet )
    {
        for ( int i = 0; i < 4; i++ )
        {
            if ( dataValueSet.getDataValues().get().get( i ).getDataElement().get().equals( dataElementId ) )
            {
                return dataValueSet.getDataValues().get().get( i );
            }
        }

        return null;
    }

    @Test
    public void testConsecutivePullsWithInterleavingFlowRuns()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Transmit Report", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );

        camelContext.start();
        syncContactsAndFetchFirstContactUuid();

        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );
        Thread.sleep( 15000 );
        assertEquals( 0, spyEndpoint.getReceivedCounter() );

        spyEndpoint.setExpectedCount( 1 );
        runFlowAndWaitUntilCompleted( flowUuid );
        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );
        spyEndpoint.await( 15, TimeUnit.SECONDS );
        assertEquals( 1, spyEndpoint.getReceivedCounter() );

        spyEndpoint.setExpectedCount( 2 );
        runFlowAndWaitUntilCompleted( flowUuid );
        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );
        spyEndpoint.await( 15, TimeUnit.SECONDS );
        assertEquals( 2, spyEndpoint.getReceivedCounter() );

        spyEndpoint.setExpectedCount( 3 );
        runFlowAndWaitUntilCompleted( flowUuid );
        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );
        spyEndpoint.await( 15, TimeUnit.SECONDS );
        assertEquals( 3, spyEndpoint.getReceivedCounter() );
    }

    @Test
    public void testPullFetchesFlowRunsByModifiedAtInAscendingOrder()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Scan RapidPro Flows",
            r -> r.weaveByType( TransformDefinition.class ).before().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );

        camelContext.start();
        syncContactsAndFetchFirstContactUuid();

        runFlowAndWaitUntilCompleted( flowUuid );
        runFlowAndWaitUntilCompleted( flowUuid );
        runFlowAndWaitUntilCompleted( flowUuid );

        spyEndpoint.setExpectedCount( 3 );
        producerTemplate.sendBody( "direct:pull", ExchangePattern.InOnly, null );
        spyEndpoint.await( 15, TimeUnit.SECONDS );

        String firstModifiedOnAsString = (String) spyEndpoint.getReceivedExchanges().get( 0 ).getMessage()
            .getBody( Map.class )
            .get( "modified_on" );
        Instant firstModifiedOn = Instant.parse( firstModifiedOnAsString );

        String secondModifiedOnAsString = (String) spyEndpoint.getReceivedExchanges().get( 1 ).getMessage()
            .getBody( Map.class )
            .get( "modified_on" );
        Instant secondModifiedOn = Instant.parse( secondModifiedOnAsString );

        String thirdModifiedOnAsString = (String) spyEndpoint.getReceivedExchanges().get( 2 ).getMessage()
            .getBody( Map.class )
            .get( "modified_on" );
        Instant thirdModifiedOn = Instant.parse( thirdModifiedOnAsString );

        assertTrue( firstModifiedOn.isBefore( secondModifiedOn ),
            String.format( "Flow run modified_on is %s (%s) while next immediate flow run modified_on is %s (%s)",
                firstModifiedOnAsString, firstModifiedOn,
                secondModifiedOnAsString, secondModifiedOn ) );
        assertTrue( secondModifiedOn.isBefore( thirdModifiedOn ),
            String.format( "Flow run modified_on is %s (%s) while next immediate flow run modified_on is %s (%s)",
                secondModifiedOnAsString, secondModifiedOn,
                thirdModifiedOnAsString, thirdModifiedOn ) );
    }

    @Test
    public void testPullGivenOrgUnitIdInFlowRunResult()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Scan RapidPro Flows",
            r -> r.weaveByToUri( "${exchangeProperty.nextRunsPageUrl}" ).replace().to( "mock:rapidPro" ) );
        MockEndpoint rapidProMockEndpoint = camelContext.getEndpoint( "mock:rapidPro", MockEndpoint.class );
        rapidProMockEndpoint.whenAnyExchangeReceived( exchange -> {
            Map<String, Object> flowRuns = objectMapper.readValue(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(
                    "flowRuns.json" ), Map.class );

            List<Map<String, Object>> results = (List<Map<String, Object>>) flowRuns.get( "results" );
            Map<String, Object> result = results.get( 0 );
            ((Map<String, Object>) result.get( "values" )).put( "org_unit_id",
                Map.of( "name", "org_unit_id", "value", "acme" ) );

            exchange.getMessage().setBody( objectMapper.writeValueAsString( flowRuns ) );
        } );

        AdviceWith.adviceWith( camelContext, "Transmit Report",
            r -> r.weaveByToUri( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" )
                .replace().to( "mock:dhis2" ) );
        MockEndpoint fakeDhis2Endpoint = camelContext.getEndpoint( "mock:dhis2", MockEndpoint.class );
        fakeDhis2Endpoint.setExpectedCount( 1 );
        fakeDhis2Endpoint.whenAnyExchangeReceived(
            exchange -> exchange.getMessage().setBody( objectMapper.writeValueAsString(
                new WebMessage().withStatus( DescriptiveWebMessage.Status.OK ) ) ) );

        camelContext.start();

        producerTemplate.sendBody( "direct:pull", null );
        fakeDhis2Endpoint.await( 10, TimeUnit.SECONDS );
        DataValueSet dataValueSet = objectMapper.readValue(
            fakeDhis2Endpoint.getReceivedExchanges().get( 0 ).getMessage().getBody( String.class ),
            DataValueSet.class );
        assertEquals( "acme", dataValueSet.getOrgUnit().get() );
    }

    @Test
    public void testPullGivenNextPage()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        AdviceWith.adviceWith( camelContext, "Scan RapidPro Flows",
            r -> r.weaveByToUri( "${exchangeProperty.nextRunsPageUrl}" ).replace().to( "mock:rapidPro" ) );
        MockEndpoint rapidProMockEndpoint = camelContext.getEndpoint( "mock:rapidPro", MockEndpoint.class );
        rapidProMockEndpoint.whenAnyExchangeReceived( exchange -> {
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
        runFlow( flowUuid );
        waitUntilFlowRunIsCompleted( flowUuid, Instant.now() );
    }

    private void waitUntilFlowRunIsCompleted( String flowUuid, Instant after )
        throws
        InterruptedException
    {
        String exitType = "";
        while ( exitType == null || !exitType.equals( "completed" ) )
        {
            exitType = given( RAPIDPRO_API_REQUEST_SPEC )
                .queryParam( "flow", flowUuid ).queryParam( "after", after.toString() ).when()
                .get( "runs.json" ).then().statusCode( 200 ).extract().path( "results[0].exit_type" );
            Thread.sleep( 1000 );
        }
    }
}
