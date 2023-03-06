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
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.support.DefaultExchange;
import org.hisp.dhis.api.model.v2_38_1.OrganisationUnit;
import org.hisp.dhis.api.model.v2_38_1.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContactDataSonnetTestCase
{
    private DatasonnetExpression dsExpression;

    private DefaultCamelContext camelContext;

    @BeforeEach
    public void beforeEach()
    {
        dsExpression = new DatasonnetExpression( "resource:classpath:contact.ds" );
        dsExpression.setResultType( Map.class );
        dsExpression.setBodyMediaType( "application/x-java-object" );
        dsExpression.setOutputMediaType( "application/x-java-object" );

        camelContext = new DefaultCamelContext();
    }

    @Test
    public void testMapping()
    {
        Exchange exchange = new DefaultExchange( camelContext );
        Map<String, Object> user = new ObjectMapper().registerModule( new Jdk8Module() ).convertValue(
            new User().withId( "AIK2aQOJIbj" ).withFirstName( "Alice" ).withSurname( "Wonderland" )
                .withPhoneNumber( "+233223232" ).withTelegram( "6249937697" )
                .withOrganisationUnits( List.of( new OrganisationUnit().withId( "lc3eMKXaEfw" ) ) ), Map.class );
        exchange.getMessage().setBody( user );
        Map<String, Object> contact = new ValueBuilder( dsExpression ).evaluate( exchange, Map.class );

        assertEquals( "Alice Wonderland", contact.get( "name" ) );
        assertEquals( 2, ((List<String>) contact.get( "urns" )).size() );
        assertEquals( "tel:+233223232", ((List<String>) contact.get( "urns" )).get( 0 ) );
        assertEquals( "telegram:6249937697", ((List<String>) contact.get( "urns" )).get( 1 ) );
    }
}
