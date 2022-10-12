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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@ActiveProfiles( "test" )
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD )
@TestPropertySource( properties = { "dhis2.api.url=", "rapidpro.api.url=mock:rapidpro", "rapidpro.api.token=" } )
public class ConfigureRapidProRouteTestCase
{
    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ProducerTemplate producerTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Test
    public void testDhis2OrgUnitIdAndDhis2UserIdFieldsAreCreatedWhenTheyDoNotExistOnRapidPro()
        throws Exception
    {
        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint(
                    "mock://rapidpro/fields.json?httpMethod=GET&key=dhis2_organisation_unit_id" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            return (T) "{\"results\":[]}";
                        }
                    } ) );

        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint( "mock://rapidpro/fields.json?httpMethod=GET&key=dhis2_user_id" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            return (T) "{\"results\":[]}";
                        }
                    } ) );

        MockEndpoint endpoint = camelContext.getEndpoint( "mock:rapidpro/fields.json?httpMethod=POST",
            MockEndpoint.class );

        camelContext.start();
        producerTemplate.sendBody( "direct:createFieldsRoute", null );
        assertEquals( 2, endpoint.getReceivedCounter() );
        assertEquals( "DHIS2 Organisation Unit ID",
            objectMapper.readValue( endpoint.getExchanges().get( 0 ).getMessage().getBody( String.class ), Map.class )
                .get( "label" ) );
        assertEquals( "DHIS2 User ID",
            objectMapper.readValue( endpoint.getExchanges().get( 1 ).getMessage().getBody( String.class ), Map.class )
                .get( "label" ) );
    }

    @Test
    public void testDhis2OrgUnitIdFieldIsCreatedWhenItDoesNotExistOnRapidPro()
        throws Exception
    {
        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint(
                    "mock://rapidpro/fields.json?httpMethod=GET&key=dhis2_organisation_unit_id" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            return (T) "{\"results\":[]}";
                        }
                    } ) );

        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint( "mock://rapidpro/fields.json?httpMethod=GET&key=dhis2_user_id" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            return (T) "{\"results\":[{}]}";
                        }
                    } ) );

        MockEndpoint endpoint = camelContext.getEndpoint( "mock:rapidpro/fields.json?httpMethod=POST",
            MockEndpoint.class );

        camelContext.start();
        producerTemplate.sendBody( "direct:createFieldsRoute", null );
        assertEquals( 1, endpoint.getReceivedCounter() );
        assertEquals( "DHIS2 Organisation Unit ID",
            objectMapper.readValue( endpoint.getExchanges().get( 0 ).getMessage().getBody( String.class ), Map.class )
                .get( "label" ) );
    }

    @Test
    public void testDhis2UserIdFieldIsCreatedWhenItDoesNotExistOnRapidPro()
        throws Exception
    {
        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint(
                    "mock://rapidpro/fields.json?httpMethod=GET&key=dhis2_organisation_unit_id" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            return (T) "{\"results\":[{}]}";
                        }
                    } ) );

        AdviceWith.adviceWith( camelContext, "Create RapidPro Fields",
            r -> r.interceptSendToEndpoint( "mock://rapidpro/fields.json?httpMethod=GET&key=dhis2_user_id" )
                .skipSendToOriginalEndpoint().setBody(
                    new Expression()
                    {
                        @Override
                        public <T> T evaluate( Exchange exchange, Class<T> type )
                        {
                            return (T) "{\"results\":[]}";
                        }
                    } ) );

        MockEndpoint endpoint = camelContext.getEndpoint( "mock:rapidpro/fields.json?httpMethod=POST",
            MockEndpoint.class );

        camelContext.start();
        producerTemplate.sendBody( "direct:createFieldsRoute", null );
        assertEquals( 1, endpoint.getReceivedCounter() );
        assertEquals( "DHIS2 User ID",
            objectMapper.readValue( endpoint.getExchanges().get( 0 ).getMessage().getBody( String.class ), Map.class )
                .get( "label" ) );
    }
}
