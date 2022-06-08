package org.hisp.dhis.integration.rapidpro.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class ReportRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doConfigure()
        throws Exception
    {
        from( "jetty:{{http.endpoint.uri:http://localhost:8081/rapidProConnector}}/dhis2queue?httpMethodRestrict=POST" )
            .removeHeaders( "*" )
            .to( "jms:queue:reports?exchangePattern=InOnly" );

        from( "jms:queue:reports" ).
            unmarshal().json( Map.class ).
            enrich( "dhis2://get/resource?path=dataStore/rapidpro-connector/indicator-mappings&client=#dhis2Client",
                ( oldExchange, newExchange ) -> {
                    try
                    {
                        oldExchange.getMessage().setHeader( "indicator-mappings", objectMapper.readValue(
                            (InputStream) newExchange.getMessage().getBody(), List.class ) );
                    }
                    catch ( IOException e )
                    {
                        throw new RuntimeException( e );
                    }
                    return oldExchange;
                } ).transform( datasonnet( "resource:classpath:dataValueSet.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .to( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" );
    }
}
