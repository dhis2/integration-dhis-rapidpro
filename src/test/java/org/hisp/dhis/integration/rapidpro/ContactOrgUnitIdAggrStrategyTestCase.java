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
package org.hisp.dhis.integration.rapidpro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.ContactOrgUnitIdAggrStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContactOrgUnitIdAggrStrategyTestCase
{
    @Test
    public void testAggregateGivenInputStreamInNewExchangeBody()
        throws
        IOException
    {
        CamelContext camelContext = new DefaultCamelContext();

        Exchange oldExchange = new DefaultExchange( camelContext );
        Message oldMessage = new DefaultMessage( camelContext );
        oldMessage.setBody( Map.of( "contact", Map.of( "uuid", UUID.randomUUID().toString() ) ) );
        oldExchange.setMessage( oldMessage );

        PipedInputStream pipedInputStream = new PipedInputStream();
        PipedOutputStream pipedOutputStream = new PipedOutputStream( pipedInputStream );
        new ObjectMapper().writeValue( pipedOutputStream,
            Map.of( "results",
                List.of( Map.of( "fields", Map.of( "dhis2_organisation_unit_id", "fdc6uOvgoji" ) ) ) ) );

        Exchange newExchange = new DefaultExchange( camelContext );
        Message newMessage = new DefaultMessage( camelContext );
        newMessage.setBody( pipedInputStream );
        newExchange.setMessage( newMessage );

        ContactOrgUnitIdAggrStrategy contactOrgUnitIdAggrStrategy = new ContactOrgUnitIdAggrStrategy();
        Exchange aggregateExchange = contactOrgUnitIdAggrStrategy.aggregate( oldExchange, newExchange );
        assertEquals( "fdc6uOvgoji", aggregateExchange.getMessage().getHeader( "orgUnitId" ) );
    }

    @Test
    public void testAggregate()
        throws
        IOException
    {
        CamelContext camelContext = new DefaultCamelContext();

        Exchange oldExchange = new DefaultExchange( camelContext );
        Message oldMessage = new DefaultMessage( camelContext );
        oldMessage.setBody( Map.of( "contact", Map.of( "uuid", UUID.randomUUID().toString() ) ) );
        oldExchange.setMessage( oldMessage );

        Exchange newExchange = new DefaultExchange( camelContext );
        Message newMessage = new DefaultMessage( camelContext );
        newMessage.setBody( new ObjectMapper().writeValueAsString( Map.of( "results",
                List.of( Map.of( "fields", Map.of( "dhis2_organisation_unit_id", "fdc6uOvgoji" ) ) ) ) ) );
        newExchange.setMessage( newMessage );

        ContactOrgUnitIdAggrStrategy contactOrgUnitIdAggrStrategy = new ContactOrgUnitIdAggrStrategy();
        Exchange aggregateExchange = contactOrgUnitIdAggrStrategy.aggregate( oldExchange, newExchange );
        assertEquals( "fdc6uOvgoji", aggregateExchange.getMessage().getHeader( "orgUnitId" ) );
    }
}
