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

import org.apache.camel.Exchange;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BroadcastContactDataSonnetTestCase
{
    private DatasonnetExpression dsExpression;

    private DefaultCamelContext camelContext;

    @BeforeEach
    public void beforeEach()
    {
        dsExpression = new DatasonnetExpression( "resource:classpath:broadcastContacts.ds" );
        dsExpression.setResultType( List.class );
        dsExpression.setBodyMediaType( "application/x-java-object" );
        dsExpression.setOutputMediaType( "application/x-java-object" );

        camelContext = new DefaultCamelContext();
        camelContext.getRegistry().bind( "native", new NativeDataSonnetLibrary() );
    }

    @Test
    public void testMapping()
    {
        Exchange exchange = new DefaultExchange( camelContext );
        exchange.setProperty( "orgUnitIdScheme", "ID" );

        exchange.setProperty( "dataSet",
            Map.of( "name", "Malaria annual data", "id", "qNtxTrp56wV", "periodType", "Yearly", "organisationUnits",
                List.of( Map.of( "id", "jUb8gELQApl" ) ) ) );


        Map<String, Set<String>> orgUnitIdsAndContactIds = Map.of( "jUb8gELQApl",
            Set.of( "fc2a8f28-e6fa-40d0-a667-8b45009f2db3", "919a5430-6983-4402-af8e-286f232ab1a1" ), "bL4ooGhyHRQ", Set.of("e7eecc70-245c-4ebc-9c10-a2b966694289"));
        exchange.setProperty( "orgUnitIdsAndContactIds", orgUnitIdsAndContactIds);

        exchange.getMessage().setBody( List.of( "jUb8gELQApl" ) );
        List<String> broadcastContacts = new ValueBuilder( dsExpression ).evaluate( exchange, List.class );

        assertEquals( 2, broadcastContacts.size() );
        assertTrue( broadcastContacts.contains( "fc2a8f28-e6fa-40d0-a667-8b45009f2db3" ) );
        assertTrue( broadcastContacts.contains( "919a5430-6983-4402-af8e-286f232ab1a1" ) );
    }
}
