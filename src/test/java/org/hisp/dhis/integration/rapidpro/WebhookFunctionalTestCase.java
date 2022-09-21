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
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebhookFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Test
    public void testWebhook()
        throws
        Exception
    {
        AdviceWith.adviceWith( camelContext, "dhis2Route", r -> r.weaveAddLast().to( "mock:spy" ) );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:spy", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        camelContext.getRegistry().bind( "selfSignedHttpClientConfigurer", new SelfSignedHttpClientConfigurer() );
        camelContext.start();
        String contactUuid = syncContactsAndFetchFirstContactUuid();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );

        producerTemplate.setDefaultEndpoint( camelContext.getEndpoint( rapidProConnectorHttpEndpointUri ) );
        Exchange responseExchange = producerTemplate.send(
            rapidProConnectorHttpEndpointUri
                + "/webhook?dataSetCode=MAL_YEARLY&httpClientConfigurer=#selfSignedHttpClientConfigurer&httpMethod=POST",
            exchange -> exchange.getMessage().setBody( String.format(  webhookMessage, contactUuid ) ) );

        assertEquals( "", responseExchange.getMessage().getBody( String.class ) );
        assertEquals( 202, responseExchange.getMessage().getHeaders().get( "CamelHttpResponseCode" ) );

        spyEndpoint.await( 10, TimeUnit.SECONDS );
        assertEquals( 1, spyEndpoint.getReceivedCounter() );
    }
}
