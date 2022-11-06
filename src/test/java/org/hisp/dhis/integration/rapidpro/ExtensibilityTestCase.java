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

import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExtensibilityTestCase extends AbstractFunctionalTestCase
{
    @Test
    public void testOverrideRoute()
        throws
        Exception
    {
        System.setProperty( "sync.rapidpro.contacts", "true" );
        System.setProperty( "report.destination.endpoint",
            "https://localhost:" + serverPort + "/dhis2rapidpro/legacy" );

        camelContext.getRegistry().bind( "selfSignedHttpClientConfigurer", new SelfSignedHttpClientConfigurer() );
        camelContext.addRoutes( new RouteBuilder()
        {
            @Override
            public void configure()
            {
                from( "servlet:legacy?httpMethodRestrict=POST" ).to( "mock:destination" ).removeHeaders( "*" );
            }
        } );
        MockEndpoint spyEndpoint = camelContext.getEndpoint( "mock:destination", MockEndpoint.class );
        spyEndpoint.setExpectedCount( 1 );

        FileUtils.forceMkdir( new File( "routes" ) );

        camelContext.start();

        FileUtils.copyFile( new File( this.getClass().getResource( "/deliverReport.yaml" ).getFile() ),
            new File( "routes/deliverReport.yaml" ) );

        while ( true )
        {
            Thread.sleep( 5000 );
            Route deliverReportRoute = camelContext.getRoute( "Deliver Report" );
            if ( deliverReportRoute.getSourceLocationShort() != null && deliverReportRoute.getSourceLocationShort()
                .equals( "deliverReport.yaml:4" ) )
            {
                break;
            }
        }

        String contactUuid = syncContactsAndFetchFirstContactUuid();
        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );
        producerTemplate.requestBody(
            dhis2RapidProHttpEndpointUri
                + "/webhook?aParam=aValue&dataSetCode=MAL_YEARLY&httpClientConfigurer=#selfSignedHttpClientConfigurer&httpMethod=POST",
            String.format( webhookMessage, contactUuid ), String.class );

        spyEndpoint.await( 10, TimeUnit.SECONDS );

        assertEquals( 1, spyEndpoint.getReceivedCounter() );
        Map<String, Object> headers = spyEndpoint.getReceivedExchanges().get( 0 ).getMessage().getHeaders();
        assertEquals( "aValue", headers.get( "aParam" ) );
        assertEquals( "Basic YWxpY2U6c2VjcmV0", headers.get( "Authorization" ) );
    }
}
