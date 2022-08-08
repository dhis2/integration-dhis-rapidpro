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

import org.hisp.dhis.api.model.v2_36_11.DataValueSet;
import org.hisp.dhis.api.model.v2_36_11.DataValue__1;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.Environment;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hisp.dhis.integration.rapidpro.Environment.DHIS2_CLIENT;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ReminderRouteBuilderFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Test
    public void testReportIsOverdue()
    {
        System.setProperty( "reminder.data.set.ids", "qNtxTrp56wV" );
        camelContext.start();
        producerTemplate.sendBody( "direct:reminders", null );
        given( RAPIDPRO_API_REQUEST_SPEC ).get( "broadcasts.json" ).then()
            .body( "results.size()", equalTo( 1 ) )
            .body( "results[0].text.eng", equalTo( "Malaria annual data report is overdue" ) );
    }

    @Test
    public void testReportIsNotOverdue()
        throws IOException, InterruptedException
    {
        System.setProperty( "reminder.data.set.ids", "VEM58nY22sO" );
        camelContext.start();

        DHIS2_CLIENT.post( "dataValueSets" ).withResource(
                new DataValueSet().withCompleteDate(
                        ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT ) )
                    .withOrgUnit( Environment.ORG_UNIT_ID )
                    .withDataSet( "VEM58nY22sO" ).withPeriod( PeriodBuilder.monthOf( new Date(), -1 ) )
                    .withDataValues(
                        List.of( new DataValue__1().withDataElement( "GEN_ALL-CAUSE_DTH_CASES" ).withValue( "20" ) ) ) )
            .withParameter( "dataElementIdScheme", "CODE" )
            .transfer().close();

        DHIS2_CLIENT.post( "maintenance" ).withParameter( "cacheClear", "true" )
            .transfer().close();
        // FIXME: cache clearing is happening asynchronously so running analytics immediately after clearing the cache may produce the wrong results
        Thread.sleep( 5000 );
        Environment.runAnalytics();

        producerTemplate.sendBody( "direct:reminders", null );
        List<Map<String, Object>> results = given( RAPIDPRO_API_REQUEST_SPEC ).get( "broadcasts.json" ).then().extract()
            .path( "results" );
        if ( results.size() != 0 )
        {
            for ( Map<String, Object> result : results )
            {
                assertNotEquals( "Malaria elimination report is overdue",
                    ((Map<String, Object>) result.get( "text" )).get( "eng" ) );
            }
        }
    }
}
