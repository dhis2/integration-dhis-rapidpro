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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.MediaTypes;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.support.DefaultExchange;
import org.hisp.dhis.integration.sdk.support.period.PeriodBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import sjsonnet.Materializer;
import sjsonnet.Val;

import com.datasonnet.header.Header;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.Library;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DataValueSetDataSonnetTestCase
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DefaultExchange exchange;

    private DatasonnetExpression dsExpression;

    private CountDownLatch logCountDownLatch;

    @BeforeEach
    public void beforeEach()
    {
        dsExpression = new DatasonnetExpression( "resource:classpath:dataValueSet.ds" );
        dsExpression.setResultType( Map.class );
        dsExpression.setBodyMediaType( "application/x-java-object" );
        dsExpression.setOutputMediaType( "application/x-java-object" );

        List<String> dataElementCodes = List.of( "GEN_EXT_FUND", "MAL-POP-TOTAL", "MAL_LLIN_DISTR_PW",
            "GEN_DOMESTIC FUND", "MAL_LLIN_DISTR_NB", "MAL_PEOPLE_PROT_BY_IRS", "MAL_POP_AT_RISK", "GEN_PREG_EXPECT",
            "GEN_FUND_NEED" );

        logCountDownLatch = new CountDownLatch( 1 );
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.getRegistry().bind( "native", new Library()
        {
            @Override
            public String namespace()
            {
                return "native";
            }

            @Override
            public Map<String, Val.Func> functions( DataFormatService dataFormats, Header header )
            {
                Map<String, Val.Func> answer = new HashMap<>();
                answer.put( "logWarning", makeSimpleFunc(
                    Collections.singletonList( "key" ), vals -> {
                        logCountDownLatch.countDown();
                        return null;
                    } ) );
                answer.put( "isCategoryOptionCombo", makeSimpleFunc(
                    Collections.singletonList( "key" ), vals -> {
                        if ( ((Val.Str) vals.get( 0 )).value().equals( "Male" ))
                        {
                            return Materializer.reverse( dataFormats.mandatoryRead(
                                new DefaultDocument( true, MediaTypes.APPLICATION_JAVA ) ) );
                        } else {
                            return Materializer.reverse( dataFormats.mandatoryRead(
                                new DefaultDocument( false, MediaTypes.APPLICATION_JAVA ) ) );
                        }
                    } ) );

                return answer;
            }

            @Override
            public Map<String, Val.Obj> modules( DataFormatService dataFormats, Header header )
            {
                return Collections.emptyMap();
            }

            @Override
            public Set<String> libsonnets()
            {
                return Collections.emptySet();
            }
        } );

        exchange = new DefaultExchange( camelContext );
        exchange.getMessage().setHeader( "orgUnitId", "fdc6uOvgoji" );
        exchange.getMessage().setHeader( "dataElementCodes", dataElementCodes );
        exchange.getMessage().setHeader( "period", PeriodBuilder.weekOf( new Date( 1657626227255L ) ) );
        exchange.getMessage().setHeader( "dataSetId", "qNtxTrp56wV" );

    }

    @AfterEach
    public void afterEach()
    {
        logCountDownLatch = new CountDownLatch( 1 );
    }

    @Test
    public void testMappingOmitsWarningWhenAllDataElementCodesAreKnown()
        throws IOException
    {
        Map<String, Object> payload = OBJECT_MAPPER.readValue( StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() ), Map.class );

        ((Map<String, Object>) payload.get( "results" )).remove( "msg" );

        exchange.getMessage().setBody( payload );

        new ValueBuilder( dsExpression ).evaluate( exchange, Map.class );
        assertEquals( 1, logCountDownLatch.getCount() );
    }

    @Test
    public void testMapping()
        throws IOException
    {
        exchange.getMessage().setBody( OBJECT_MAPPER.readValue( StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() ), Map.class ) );

        Map dataValueSet = new ValueBuilder( dsExpression ).evaluate( exchange, Map.class );

        assertNotNull( dataValueSet.get( "completedDate" ) );
        assertNull( dataValueSet.get( "attributeOptionCombo" ) );
        assertEquals( "fdc6uOvgoji", dataValueSet.get( "orgUnit" ) );
        assertEquals( "qNtxTrp56wV", dataValueSet.get( "dataSet" ) );
        assertEquals( "2022W28", dataValueSet.get( "period" ) );

        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) dataValueSet.get( "dataValues" );
        assertEquals( 4, dataValues.size() );
        assertEquals( "GEN_EXT_FUND", dataValues.get( 0 ).get( "dataElement" ) );
        assertEquals( "2", dataValues.get( 0 ).get( "value" ) );
        assertEquals( "MAL-POP-TOTAL", dataValues.get( 1 ).get( "dataElement" ) );
        assertEquals( "10", dataValues.get( 1 ).get( "value" ) );
        assertEquals( "MAL_LLIN_DISTR_PW", dataValues.get( 2 ).get( "dataElement" ) );
        assertEquals( "3", dataValues.get( 2 ).get( "value" ) );
        assertEquals( "GEN_DOMESTIC FUND", dataValues.get( 3 ).get( "dataElement" ) );
        assertEquals( "5", dataValues.get( 3 ).get( "value" ) );

        assertEquals(
            "RapidPro contact details: \"{\\n \\\"name\\\": \\\"John Doe\\\",\\n \\\"urn\\\": \\\"tel:+12065551212\\\",\\n \\\"uuid\\\": \\\"%s\\\"\\n}\"",
            dataValues.get( 0 ).get( "comment" ) );

        assertEquals( 0, logCountDownLatch.getCount() );
    }

    @Test
    public void testMappingGivenValidCategoryOptionComboCode()
        throws IOException
    {
        Map<String, Object> payload = OBJECT_MAPPER.readValue( StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() ), Map.class );

        ((Map<String, Object>) ((Map<String, Object>) payload.get( "results" )).get( "mal_llin_distr_pw" )).put(
            "category", "Male" );

        exchange.getMessage().setBody( payload );

        Map dataValueSet = new ValueBuilder( dsExpression ).evaluate( exchange, Map.class );

        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) dataValueSet.get( "dataValues" );

        assertEquals( "GEN_EXT_FUND", dataValues.get( 0 ).get( "dataElement" ) );
        assertNull( dataValues.get( 0 ).get( "categoryOptionCombo" ) );
        assertEquals( "2", dataValues.get( 0 ).get( "value" ) );

        assertEquals( "MAL-POP-TOTAL", dataValues.get( 1 ).get( "dataElement" ) );
        assertNull( dataValues.get( 1 ).get( "categoryOptionCombo" ) );
        assertEquals( "10", dataValues.get( 1 ).get( "value" ) );

        assertEquals( "MAL_LLIN_DISTR_PW", dataValues.get( 2 ).get( "dataElement" ) );
        assertEquals( "Male", dataValues.get( 2 ).get( "categoryOptionCombo" ) );
        assertEquals( "3", dataValues.get( 2 ).get( "value" ) );

        assertEquals( "GEN_DOMESTIC FUND", dataValues.get( 3 ).get( "dataElement" ) );
        assertNull( dataValues.get( 3 ).get( "categoryOptionCombo" ) );
        assertEquals( "5", dataValues.get( 3 ).get( "value" ) );

        assertEquals( 0, logCountDownLatch.getCount() );
    }

    @Test
    public void testMappingGivenInvalidCategoryOptionComboCode()
        throws IOException
    {
        Map<String, Object> payload = OBJECT_MAPPER.readValue( StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() ), Map.class );

        ((Map<String, Object>) ((Map<String, Object>) payload.get( "results" )).get( "mal_llin_distr_pw" )).put(
            "category", "M" );

        exchange.getMessage().setBody( payload );

        Map dataValueSet = new ValueBuilder( dsExpression ).evaluate( exchange, Map.class );

        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) dataValueSet.get( "dataValues" );

        assertEquals( "GEN_EXT_FUND", dataValues.get( 0 ).get( "dataElement" ) );
        assertNull( dataValues.get( 0 ).get( "categoryOptionCombo" ) );
        assertEquals( "2", dataValues.get( 0 ).get( "value" ) );

        assertEquals( "MAL-POP-TOTAL", dataValues.get( 1 ).get( "dataElement" ) );
        assertNull( dataValues.get( 1 ).get( "categoryOptionCombo" ) );
        assertEquals( "10", dataValues.get( 1 ).get( "value" ) );

        assertEquals( "MAL_LLIN_DISTR_PW", dataValues.get( 2 ).get( "dataElement" ) );
        assertNull( dataValues.get( 2 ).get( "categoryOptionCombo" ) );
        assertEquals( "3", dataValues.get( 2 ).get( "value" ) );

        assertEquals( "GEN_DOMESTIC FUND", dataValues.get( 3 ).get( "dataElement" ) );
        assertNull( dataValues.get( 3 ).get( "categoryOptionCombo" ) );
        assertEquals( "5", dataValues.get( 3 ).get( "value" ) );

        assertEquals( 0, logCountDownLatch.getCount() );
    }
}
