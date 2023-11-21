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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.rapidpro.ProgramStageToFlowMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FetchScheduledTrackerEventsFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ProgramStageToFlowMap programStageToFlowMap;

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
    public void testDueEventsCountWithInvalidProgramStageThrowsException()
        throws
        Exception
    {
        Environment.createDhis2TrackedEntitiesWithEnrollment( Environment.ORG_UNIT_ID, 1, List.of( "ZP5HZ87wzc0" ) );
        programStageToFlowMap.add( "invalid", "invalid-flow-uuid-placeholder" );
        camelContext.start();

        assertThrows( RuntimeCamelException.class, () -> {
            producerTemplate.sendBody( "direct:fetchDueEvents", ExchangePattern.InOnly, null );
        } );
        programStageToFlowMap.remove( "invalid" );
    }

    @Test
    public void testDueEventsCountsUnderLoad()
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

}
