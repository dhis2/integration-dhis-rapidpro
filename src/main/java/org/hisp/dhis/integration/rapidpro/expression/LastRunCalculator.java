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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class LastRunCalculator implements Expression
{
    @Override
    public <T> T evaluate( Exchange exchange, Class<T> type )
    {
        Date newLastRunAt = (Date) exchange.getMessage().getHeader( "newLastRunAt" );
        Map<String, Object> body = exchange.getMessage().getBody( Map.class );
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.get( "results" );
        for ( Map<String, Object> result : results )
        {
            String exitedOn = (String) result.get( "exited_on" );
            if ( exitedOn == null )
            {
                String modifiedOn = (String) result.get( "modified_on" );
                Date modifiedOnAsDate = Date.from( Instant.parse( modifiedOn ) );
                if ( modifiedOnAsDate.before( newLastRunAt ) )
                {
                    newLastRunAt = modifiedOnAsDate;
                }
            }
        }

        return (T) newLastRunAt;
    }
}
