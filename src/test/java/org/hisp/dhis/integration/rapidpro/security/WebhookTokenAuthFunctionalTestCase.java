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
package org.hisp.dhis.integration.rapidpro.security;

import org.apache.camel.CamelExecutionException;
import org.hisp.dhis.integration.rapidpro.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.rapidpro.SelfSignedHttpClientConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertThrows;

@TestPropertySource( properties = { "webhook.security.auth=token" } )
public class WebhookTokenAuthFunctionalTestCase extends AbstractFunctionalTestCase
{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void doBeforeEach()
    {
        jdbcTemplate.execute( "TRUNCATE TABLE token" );
        jdbcTemplate.execute( "INSERT INTO TOKEN (value_) VALUES ('secret')" );
    }

    @Test
    public void testWebhookGivenCorrectToken()
        throws
        IOException
    {
        camelContext.getRegistry().bind( "selfSignedHttpClientConfigurer", new SelfSignedHttpClientConfigurer() );
        camelContext.start();
        String contactUuid = syncContactsAndFetchFirstContactUuid();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );

        producerTemplate.sendBodyAndHeader(
            dhis2RapidProHttpEndpointUri
                + "/webhook?dataSetCode=MAL_YEARLY&httpClientConfigurer=#selfSignedHttpClientConfigurer&httpMethod=POST",
            String.format( webhookMessage, contactUuid ), "Authorization",
            "Token secret" );
    }

    @Test
    public void testWebhookGivenWrongToken()
        throws
        IOException
    {
        camelContext.getRegistry().bind( "selfSignedHttpClientConfigurer", new SelfSignedHttpClientConfigurer() );
        camelContext.start();
        String contactUuid = syncContactsAndFetchFirstContactUuid();

        String webhookMessage = StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() );

        assertThrows( CamelExecutionException.class, () -> producerTemplate.sendBodyAndHeader(
            dhis2RapidProHttpEndpointUri
                + "/webhook?dataSetCode=MAL_YEARLY&httpClientConfigurer=#selfSignedHttpClientConfigurer&httpMethod=POST",
            String.format( webhookMessage, contactUuid ), "Authorization",
            "Token wrong" ) );
    }
}