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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

import org.apache.camel.ExchangePattern;
import org.hisp.dhis.api.model.v2_37_7.DataValueSet;
import org.hisp.dhis.api.model.v2_37_7.DataValue__1;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.StreamUtils;

public class DataValueSetRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Test
    @DirtiesContext
    public void testDataValueSetIsCreated()
        throws IOException
    {
        camelContext.start();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBody( "jms:queue:reports",
            ExchangePattern.InOut, String.format( webhookMessage, Environment.ORG_UNIT_ID ) );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
            "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", PeriodBuilder.yearOf( new Date(), -1 ) ).withParameter( "dataSet", "qNtxTrp56wV" )
            .transfer()
            .returnAs(
                DataValueSet.class );

        DataValue__1 externalValueDataValue = dataValueSet.getDataValues().get().get( 0 );
        assertEquals( "2", externalValueDataValue.getValue().get() );
    }

    @Test
    @DirtiesContext
    public void testDataValueSetIsCreatedGivenOrgUnitIdSchemeIsCode()
        throws IOException
    {
        System.setProperty( "org.unit.id.scheme", "CODE" );
        camelContext.start();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.sendBody( "jms:queue:reports",
            ExchangePattern.InOut, String.format( webhookMessage, "ACME" ) );

        DataValueSet dataValueSet = Environment.DHIS2_CLIENT.get(
                "dataValueSets" ).withParameter( "orgUnit", Environment.ORG_UNIT_ID )
            .withParameter( "period", PeriodBuilder.yearOf( new Date(), -1 ) ).withParameter( "dataSet", "qNtxTrp56wV" )
            .transfer()
            .returnAs(
                DataValueSet.class );

        DataValue__1 externalValueDataValue = dataValueSet.getDataValues().get().get( 0 );
        assertEquals( "2", externalValueDataValue.getValue().get() );
    }
}
