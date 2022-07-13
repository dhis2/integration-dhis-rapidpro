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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.support.DefaultExchange;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ExpressionTestCase
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testDataValueSetDataSonnetExpression()
        throws IOException
    {
        DatasonnetExpression dsExpression = new DatasonnetExpression( "resource:classpath:dataValueSet.ds" );
        dsExpression.setResultType( Map.class );
        dsExpression.setBodyMediaType( "application/x-java-object" );
        dsExpression.setOutputMediaType( "application/x-java-object" );

        List<String> dataElementCodes = List.of( "GEN_EXT_FUND", "MAL-POP-TOTAL", "MAL_LLIN_DISTR_PW",
            "GEN_DOMESTIC FUND", "MAL_LLIN_DISTR_NB", "MAL_PEOPLE_PROT_BY_IRS", "MAL_POP_AT_RISK", "GEN_PREG_EXPECT",
            "GEN_FUND_NEED" );

        Exchange exchange = new DefaultExchange( new DefaultCamelContext() );
        exchange.getMessage().setHeader( "dataElementCodes", dataElementCodes );
        exchange.getMessage().setHeader( "period", PeriodBuilder.weekOf( new Date( 1657626227255L ) ) );
        exchange.getMessage().setBody( OBJECT_MAPPER.readValue( StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() ), Map.class ) );

        Map<String, Object> dataValueSet = new ValueBuilder( dsExpression ).evaluate( exchange, Map.class );
        assertNotNull( dataValueSet.get( "completedDate" ) );
        assertNull( dataValueSet.get( "attributeOptionCombo" ) );
        assertEquals( "%s", dataValueSet.get( "orgUnit" ) );
        assertEquals( "qNtxTrp56wV", dataValueSet.get( "dataSet" ) );
        assertEquals( "2022W28", dataValueSet.get( "period" ) );

        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) dataValueSet.get( "dataValues" );
        assertEquals( "GEN_EXT_FUND", dataValues.get( 0 ).get( "dataElement" ) );
        assertEquals( "2", dataValues.get( 0 ).get( "value" ) );
        assertEquals( "MAL-POP-TOTAL", dataValues.get( 1 ).get( "dataElement" ) );
        assertEquals( "10", dataValues.get( 1 ).get( "value" ) );
        assertEquals( "MAL_LLIN_DISTR_PW", dataValues.get( 2 ).get( "dataElement" ) );
        assertEquals( "3", dataValues.get( 2 ).get( "value" ) );
        assertEquals( "GEN_DOMESTIC FUND", dataValues.get( 3 ).get( "dataElement" ) );
        assertEquals( "5", dataValues.get( 3 ).get( "value" ) );
    }
}
