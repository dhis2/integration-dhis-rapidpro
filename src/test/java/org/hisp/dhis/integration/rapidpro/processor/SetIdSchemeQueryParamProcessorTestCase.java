package org.hisp.dhis.integration.rapidpro.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SetIdSchemeQueryParamProcessorTestCase
{

    @Test
    public void testProcess()
    {
        SetIdSchemeQueryParamProcessor setIdSchemeQueryParamProcessor = new SetIdSchemeQueryParamProcessor();
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.getPropertiesComponent().addInitialProperty( "org.unit.id.scheme", "ID" );
        Exchange exchange = new DefaultExchange( camelContext );
        setIdSchemeQueryParamProcessor.process( exchange );

        Map queryParams = exchange.getMessage().getHeader( "CamelDhis2.queryParams", Map.class );
        assertEquals( "CODE", queryParams.get( "dataElementIdScheme" ) );
        assertEquals( "CODE", queryParams.get( "categoryOptionComboIdScheme" ) );
    }
}
