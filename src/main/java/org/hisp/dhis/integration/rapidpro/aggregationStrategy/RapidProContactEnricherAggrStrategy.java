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
package org.hisp.dhis.integration.rapidpro.aggregationStrategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RapidProContactEnricherAggrStrategy extends AbstractAggregationStrategy
{
    protected static final Logger LOGGER = LoggerFactory.getLogger( RapidProContactEnricherAggrStrategy.class );

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected Exchange doAggregate( Exchange oldExchange, Exchange newExchange )
        throws
        Exception
    {
        Map<String, Object> originalBody = oldExchange.getMessage().getBody( Map.class );
        String resourceBody = newExchange.getMessage().getBody( String.class );
        Map<String, Object> resourceBodyMap = objectMapper.readValue( resourceBody, Map.class );
        if ( resourceBodyMap.get( "results" ) != null )
        {
            originalBody.put( "results", resourceBodyMap.get( "results" ) );
        }
        else
        {
            LOGGER.warn( String.format( "unexpected result from RapidPro => %s", resourceBody ) );
        }
        oldExchange.getMessage().setBody( originalBody );
        return oldExchange;
    }
}

