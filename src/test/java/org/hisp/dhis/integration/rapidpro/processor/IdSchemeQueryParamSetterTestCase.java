package org.hisp.dhis.integration.rapidpro.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdSchemeQueryParamSetterTestCase
{
    @Test
    public void testProcess()
    {
        IdSchemeQueryParamSetter idSchemeQueryParamSetter = new IdSchemeQueryParamSetter();
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.getPropertiesComponent().addInitialProperty( "org.unit.id.scheme", "ID" );
        Exchange exchange = new DefaultExchange( camelContext );
        idSchemeQueryParamSetter.process( exchange );

        Map queryParams = exchange.getMessage().getHeader( "CamelDhis2.queryParams", Map.class );
        assertEquals( "CODE", queryParams.get( "dataElementIdScheme" ) );
        assertEquals( "CODE", queryParams.get( "categoryOptionComboIdScheme" ) );
    }
}
