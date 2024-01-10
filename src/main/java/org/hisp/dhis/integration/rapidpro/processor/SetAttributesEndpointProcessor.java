package org.hisp.dhis.integration.rapidpro.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.language.simple.SimpleLanguage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SetAttributesEndpointProcessor implements Processor
{
    @Override
    public void process( Exchange exchange )
        throws
        Exception
    {
        Map<String, Object> body = exchange.getMessage().getBody( Map.class );
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) body.get( "attributes" );
        boolean isAttributesEmpty = (attributes == null || attributes.isEmpty());
        String attributesEndpoint = isAttributesEmpty
            ? "dhis2://get/resource?path=tracker/trackedEntities/${body[trackedEntity]}&fields=attributes[attribute,code,value]&client=#dhis2Client"
            : "dhis2://get/resource?path=tracker/enrollments/${body[enrollment]}&fields=attributes[attribute,code,value]&client=#dhis2Client";
        String evaluatedValue = SimpleLanguage.simple( attributesEndpoint ).evaluate( exchange, String.class );
        exchange.setProperty( "attributesEndpoint", evaluatedValue );
    }
}
