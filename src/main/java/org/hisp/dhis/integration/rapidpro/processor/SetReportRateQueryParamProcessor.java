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
package org.hisp.dhis.integration.rapidpro.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hisp.dhis.api.model.v40_0.DataSet;
import org.hisp.dhis.api.model.v40_0.OrganisationUnit;
import org.hisp.dhis.api.model.v40_0.RefOrganisationUnit;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SetReportRateQueryParamProcessor implements Processor
{
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void process( Exchange exchange )
    {
        Map<String, Object> dataSetAsMap = exchange.getProperty( "dataSet", Map.class );
        DataSet dataSet = objectMapper.convertValue( dataSetAsMap, DataSet.class );

        Map<String, Object> contacts = (Map<String, Object>) exchange.getProperty( "contacts" );
        Set<String> contactOrgUnitIds = reduceOrgUnitIds( (List<Map<String, Object>>) contacts.get( "results" ) );

        String periodType = dataSet.getPeriodType().get().value();
        String lastElapsedPeriod = createLastElapsedPeriod( periodType );

        if ( dataSet.getOrganisationUnits().isPresent() && !dataSet.getOrganisationUnits().get().isEmpty() )
        {
            String orgUnitIdScheme = exchange.getProperty( "orgUnitIdScheme", String.class ).toLowerCase();
            StringBuilder ouDimensionStringBuilder = new StringBuilder();
            for ( RefOrganisationUnit organisationUnit : dataSet.getOrganisationUnits().get() )
            {
                if ( contactOrgUnitIds.contains( ((Optional<String>) organisationUnit.get( orgUnitIdScheme )).get() ) )
                {
                    ouDimensionStringBuilder.append( organisationUnit.getId() ).append( ";" );
                }
            }
            Map<String, Object> queryParams = Map.of( "dimension",
                List.of( String.format( "dx:%s.REPORTING_RATE", dataSet.getId().get() ),
                    String.format( "ou:%s", ouDimensionStringBuilder ) ), "columns", "dx",
                "rows", "ou",
                "tableLayout", "true", "hideEmptyRows", "true", "displayProperty", "SHORTNAME", "includeNumDen",
                "false", "filter", String.format( "pe:%s", lastElapsedPeriod ) );

            exchange.getMessage().setHeader( "CamelDhis2.queryParams", queryParams );
        }
    }

    protected String createLastElapsedPeriod( String periodType )
    {
        String lastElapsedPeriod;
        int periodOffset = -1;
        if ( periodType.equalsIgnoreCase( "daily" ) )
        {
            lastElapsedPeriod = PeriodBuilder.dayOf( new Date(), periodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "weekly" ) )
        {
            lastElapsedPeriod = PeriodBuilder.weekOf( new Date(), periodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "monthly" ) )
        {
            lastElapsedPeriod = PeriodBuilder.monthOf( new Date(), periodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "bimonthly" ) )
        {
            lastElapsedPeriod = PeriodBuilder.biMonthOf( new Date(), periodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "sixmonthly" ) )
        {
            lastElapsedPeriod = PeriodBuilder.sixMonthOf( new Date(), periodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "yearly" ) )
        {
            lastElapsedPeriod = PeriodBuilder.yearOf( new Date(), periodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "financialnov" ) )
        {
            lastElapsedPeriod = PeriodBuilder.financialYearStartingNovOf( new Date(), periodOffset );
        }
        else
        {
            throw new UnsupportedOperationException();
        }

        return lastElapsedPeriod;
    }

    protected Set<String> reduceOrgUnitIds( List<Map<String, Object>> contacts )
    {
        return contacts.stream()
            .map( c -> ((Map<String, String>) c.get( "fields" )).get( "dhis2_organisation_unit_id" ) ).collect(
                Collectors.toSet() );
    }
}
