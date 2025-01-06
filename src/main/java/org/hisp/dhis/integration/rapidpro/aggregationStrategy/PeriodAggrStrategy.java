package org.hisp.dhis.integration.rapidpro.aggregationStrategy;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

public class PeriodAggrStrategy implements AggregationStrategy
{
    @Override
    public Exchange aggregate( Exchange oldExchange, Exchange newExchange )
    {
        oldExchange.getMessage().setHeader( "period", newExchange.getMessage().getBody() );
        return oldExchange;
    }
}
