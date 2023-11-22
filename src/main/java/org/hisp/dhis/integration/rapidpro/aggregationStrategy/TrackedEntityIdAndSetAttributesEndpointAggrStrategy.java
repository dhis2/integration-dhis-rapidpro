package org.hisp.dhis.integration.rapidpro.aggregationStrategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.language.simple.SimpleLanguage;
import org.hisp.dhis.integration.rapidpro.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TrackedEntityIdAndSetAttributesEndpointAggrStrategy implements AggregationStrategy
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(
        TrackedEntityIdAndSetAttributesEndpointAggrStrategy.class );

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Exchange aggregate( Exchange original, Exchange resource )
    {
        Map<String, Object> originalBodyMap = original.getMessage().getBody( Map.class );
        Map<String, Object> resourceBodyMap = JsonUtils.parseJsonStringToMap(
            resource.getMessage().getBody( String.class ) );

        String trackedEntity = (String) resourceBodyMap.get( "trackedEntity" );
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) resourceBodyMap.get( "attributes" );
        boolean isAttributesEmpty = (attributes == null || attributes.isEmpty());
        String attributesEndpoint = isAttributesEmpty
            ? "dhis2://get/resource?path=tracker/trackedEntities/${body[trackedEntity]}&fields=attributes[attribute,code,value]&client=#dhis2Client"
            : "dhis2://get/resource?path=tracker/enrollments/${body[enrollment]}&fields=attributes[attribute,code,value]&client=#dhis2Client";
        originalBodyMap.put( "trackedEntity", trackedEntity );
        original.getIn().setBody( originalBodyMap );
        String evaluatedValue = SimpleLanguage.simple( attributesEndpoint ).evaluate( original, String.class );
        original.setProperty( "attributesEndpoint", evaluatedValue );
        return original;

    }
}
