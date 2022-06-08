package org.hisp.dhis.integration.rapidpro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DataValueSetDataSonnetTestCase
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testMapping()
        throws IOException
    {
        DatasonnetExpression dsExpression = new DatasonnetExpression( "resource:classpath:dataValueSet.ds" );
        dsExpression.setResultType( Map.class );
        dsExpression.setBodyMediaType( "application/x-java-object" );
        dsExpression.setOutputMediaType( "application/x-java-object" );

        Exchange exchange = new DefaultExchange( new DefaultCamelContext() );
        exchange.getMessage().setHeader( "indicator-mappings", OBJECT_MAPPER.readValue( StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "indicator-mappings.json" ),
            Charset.defaultCharset() ), List.class ) );
        exchange.getMessage().setBody( OBJECT_MAPPER.readValue( StreamUtils.copyToString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "webhook.json" ),
            Charset.defaultCharset() ), Map.class ) );

        Map<String, Object> dataValueSet = new ValueBuilder( dsExpression ).evaluate( exchange, Map.class );
        assertNotNull( dataValueSet.get( "completedDate" ) );
        assertNull( dataValueSet.get( "attributeOptionCombo" ) );
        assertEquals( "%s", dataValueSet.get( "orgUnit" ) );
        assertEquals( "qNtxTrp56wV", dataValueSet.get( "dataSet" ) );
        assertEquals( "2021W19", dataValueSet.get( "period" ) );

        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) dataValueSet.get( "dataValues" );
        assertEquals( "tpz77FcntKx", dataValues.get( 0 ).get( "dataElement" ) );
        assertEquals( "2", dataValues.get( 0 ).get( "value" ) );
        assertEquals( "dFaBg0HpoIL", dataValues.get( 1 ).get( "dataElement" ) );
        assertEquals( "10", dataValues.get( 1 ).get( "value" ) );
        assertEquals( "UH47dKFqTRK", dataValues.get( 2 ).get( "dataElement" ) );
        assertEquals( "3", dataValues.get( 2 ).get( "value" ) );
        assertEquals( "EpyvZBsqMmM", dataValues.get( 3 ).get( "dataElement" ) );
        assertEquals( "5", dataValues.get( 3 ).get( "value" ) );
    }
}
