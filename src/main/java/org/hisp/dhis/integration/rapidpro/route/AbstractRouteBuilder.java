package org.hisp.dhis.integration.rapidpro.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRouteBuilder extends RouteBuilder
{
    protected static final Logger LOGGER = LoggerFactory.getLogger( AbstractRouteBuilder.class );

    @Override
    public void configure()
        throws Exception
    {
        onException( HttpOperationFailedException.class )
            .log( LoggingLevel.ERROR, LOGGER,
                "HTTP response body => ${exchangeProperty.CamelExceptionCaught.responseBody}" )
            .process( exchange -> {
                throw (Exception) exchange.getProperty( Exchange.EXCEPTION_CAUGHT );
            } );

        doConfigure();
    }

    protected abstract void doConfigure()
        throws Exception;
}
