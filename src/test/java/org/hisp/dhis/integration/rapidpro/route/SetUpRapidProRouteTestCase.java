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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.hisp.dhis.integration.rapidpro.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest( classes = Application.class )
@CamelSpringBootTest
@UseAdviceWith
@ActiveProfiles( "test" )
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD )
@TestPropertySource( properties = { "dhis2.api.url=http://dhis2.test/api", "rapidpro.api.url=mock:rapidpro", "test.connection.startup=false",
    "rapidpro.api.token=3048a3b9a04c1948aa5a7fd06e7592ba5a17d3d0" } )
public class SetUpRapidProRouteTestCase
{
    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ProducerTemplate producerTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Test
    public void testDhis2OrgUnitIdAndDhis2UserIdFieldsAreCreatedWhenTheyDoNotExistOnRapidPro()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint(
                    "kamelet:hie-rapidpro-get-fields-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            return (T) List.of();
                        }
                    } ) );

        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.weaveByToUri( "kamelet:hie-rapidpro-create-field-sink*" )
                .replace().to( "mock:hie-rapidpro-create-field-sink" ) );

        MockEndpoint endpoint = camelContext.getEndpoint( "mock:hie-rapidpro-create-field-sink",
            MockEndpoint.class );

        camelContext.start();
        producerTemplate.sendBody( "direct:createFieldsRoute", null );
        assertEquals( 2, endpoint.getReceivedCounter() );
        assertEquals( "DHIS2 Organisation Unit ID", endpoint.getExchanges().get( 0 ).getMessage().getHeader( "label", String.class ) );
        assertEquals( "DHIS2 User ID", endpoint.getExchanges().get( 1 ).getMessage().getHeader( "label", String.class ) );
    }

    @Test
    public void testDhis2OrgUnitIdFieldIsCreatedWhenItDoesNotExistOnRapidPro()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint(
                    "kamelet:hie-rapidpro-get-fields-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            if (exchange.getMessage().getHeader( "key", String.class).equals( "dhis2_organisation_unit_id" )) {
                                return (T) List.of();
                            } else {
                                return (T) List.of(Map.of());
                            }
                        }
                    } ) );


        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.weaveByToUri( "kamelet:hie-rapidpro-create-field-sink*" )
                .replace().to( "mock:hie-rapidpro-create-field-sink" ) );

        MockEndpoint endpoint = camelContext.getEndpoint( "mock:hie-rapidpro-create-field-sink",
            MockEndpoint.class );

        camelContext.start();
        producerTemplate.sendBody( "direct:createFieldsRoute", null );
        assertEquals( 1, endpoint.getReceivedCounter() );
        assertEquals( "DHIS2 Organisation Unit ID", endpoint.getExchanges().get( 0 ).getMessage().getHeader( "label", String.class ) );
    }

    @Test
    public void testDhis2UserIdFieldIsCreatedWhenItDoesNotExistOnRapidPro()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint(
                    "kamelet:hie-rapidpro-get-fields-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            if (exchange.getMessage().getHeader( "key", String.class).equals( "dhis2_user_id" )) {
                                return (T) List.of();
                            } else {
                                return (T) List.of(Map.of());
                            }
                        }
                    } ) );


        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.weaveByToUri( "kamelet:hie-rapidpro-create-field-sink*" )
                .replace().to( "mock:hie-rapidpro-create-field-sink" ) );

        MockEndpoint endpoint = camelContext.getEndpoint( "mock:hie-rapidpro-create-field-sink",
            MockEndpoint.class );

        camelContext.start();
        producerTemplate.sendBody( "direct:createFieldsRoute", null );
        assertEquals( 1, endpoint.getReceivedCounter() );
        assertEquals( "DHIS2 User ID", endpoint.getExchanges().get( 0 ).getMessage().getHeader( "label", String.class ) );
    }
}
