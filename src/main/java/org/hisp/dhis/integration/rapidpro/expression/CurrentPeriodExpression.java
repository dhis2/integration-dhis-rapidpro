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
package org.hisp.dhis.integration.rapidpro.expression;

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CurrentPeriodExpression implements Expression
{
    enum PeriodType
    {
        DAILY,
        WEEKLY,
        MONTHlY,
        BI_MONTHLY,
        SIX_MONTHLY,
        YEARLY,
        FINANCIAL_YEAR_NOV
    }

    @Value( "${report.period.type}" )
    private PeriodType periodType;

    @Value( "${report.period.offset:0}" )
    private int periodOffset;

    @Override
    public <T> T evaluate( Exchange exchange, Class<T> type )
    {
        if ( periodType.equals( PeriodType.DAILY ) )
        {
            return (T) PeriodBuilder.dayOf( new Date(), periodOffset );
        }
        else if ( periodType.equals( PeriodType.WEEKLY ) )
        {
            return (T) PeriodBuilder.weekOf( new Date(), periodOffset );
        }
        else if ( periodType.equals( PeriodType.MONTHlY ) )
        {
            return (T) PeriodBuilder.monthOf( new Date(), periodOffset );
        }
        else if ( periodType.equals( PeriodType.BI_MONTHLY ) )
        {
            return (T) PeriodBuilder.biMonthOf( new Date(), periodOffset );
        }
        else if ( periodType.equals( PeriodType.SIX_MONTHLY ) )
        {
            return (T) PeriodBuilder.sixMonthOf( new Date(), periodOffset );
        }
        else if ( periodType.equals( PeriodType.YEARLY ) )
        {
            return (T) PeriodBuilder.yearOf( new Date(), periodOffset );
        }
        else if ( periodType.equals( PeriodType.FINANCIAL_YEAR_NOV ) )
        {
            return (T) PeriodBuilder.financialYearStartingNovOf( new Date(), periodOffset );
        }
        else
        {
            throw new UnsupportedOperationException();
        }
    }
}
