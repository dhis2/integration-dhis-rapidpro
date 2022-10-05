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

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hisp.dhis.api.model.v2_36_11.DataSet;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.springframework.stereotype.Component;

@Component
public class CurrentPeriodCalculator implements Processor
{
    @Override
    public void process( Exchange exchange )
        throws Exception
    {
        Iterable<DataSet> dataSets = exchange.getMessage().getBody( Iterable.class );
        String periodType = (String) dataSets.iterator().next().getPeriodType().get();
        int reportPeriodOffset = exchange.getMessage().getHeader( "reportPeriodOffset", Integer.class );
        String period;
        if ( periodType.equalsIgnoreCase( "Daily" ) )
        {
            period = PeriodBuilder.dayOf( new Date(), reportPeriodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "Weekly" ) )
        {
            period = PeriodBuilder.weekOf( new Date(), reportPeriodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "Monthly" ) )
        {
            period = PeriodBuilder.monthOf( new Date(), reportPeriodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "BiMonthly" ) )
        {
            period = PeriodBuilder.biMonthOf( new Date(), reportPeriodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "SixMonthly" ) )
        {
            period = PeriodBuilder.sixMonthOf( new Date(), reportPeriodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "Yearly" ) )
        {
            period = PeriodBuilder.yearOf( new Date(), reportPeriodOffset );
        }
        else if ( periodType.equalsIgnoreCase( "FinancialYearNov" ) )
        {
            period = PeriodBuilder.financialYearStartingNovOf( new Date(), reportPeriodOffset );
        }
        else
        {
            throw new UnsupportedOperationException();
        }

        exchange.getMessage().setBody( period );
    }
}
