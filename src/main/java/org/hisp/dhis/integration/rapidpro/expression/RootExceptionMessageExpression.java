package org.hisp.dhis.integration.rapidpro.expression;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

@Component
public class RootExceptionMessageExpression implements Expression
{
    @Override
    public <T> T evaluate( Exchange exchange, Class<T> type )
    {
        return (T) NestedExceptionUtils.getRootCause( (Throwable) exchange.getProperty(Exchange.EXCEPTION_CAUGHT) ).getMessage();
    }
}
