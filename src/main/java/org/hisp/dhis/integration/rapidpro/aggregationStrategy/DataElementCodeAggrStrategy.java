package org.hisp.dhis.integration.rapidpro.aggregationStrategy;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

import java.util.List;

import static org.apache.camel.builder.Builder.jsonpath;

public class DataElementCodeAggrStrategy implements AggregationStrategy
{
    @Override
    public Exchange aggregate( Exchange oldExchange, Exchange newExchange )
    {
        oldExchange.getMessage().setHeader( "dataElementCodes", jsonpath( "$.dataElements..code" ).evaluate( newExchange, List.class ) );
        return oldExchange;
    }
}
