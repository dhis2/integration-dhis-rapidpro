/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.rapidpro.route;

import java.util.List;
import java.util.Map;

import org.apache.camel.LoggingLevel;
import org.hisp.dhis.integration.rapidpro.expression.PeriodExpression;
import org.hisp.dhis.integration.rapidpro.expression.RootExceptionMessageExpression;
import org.hisp.dhis.integration.rapidpro.processor.IdSchemeProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataValueSetRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private PeriodExpression periodExpression;

    @Autowired
    private RootExceptionMessageExpression rootExceptionMessageExpression;

    @Autowired
    private IdSchemeProcessor idSchemeProcessor;

    @Override
    protected void doConfigure()
    {
        from( "jetty:{{http.endpoint.uri:http://0.0.0.0:8081/rapidProConnector}}/webhook?httpMethodRestrict=POST" )
            .removeHeaders( "*" )
            .to( "jms:queue:dhis2?exchangePattern=InOnly" );

        from( "timer://retry?fixedRate=true&period=5000" )
            .setBody( constant( "SELECT id, payload FROM DEAD_LETTER_CHANNEL WHERE status = 'RETRY' LIMIT 100" ) )
            .to( "jdbc:dataSource" )
            .split().body()
            .setHeader( "id", simple( "${body['ID']}" ) )
            .log( LoggingLevel.INFO, LOGGER, "Retrying row with ID ${header.id}" )
            .setBody( simple( "${body['PAYLOAD']}" ) )
            .to( "jms:queue:dhis2?exchangePattern=InOnly" )
            .setBody( constant(
                "UPDATE DEAD_LETTER_CHANNEL SET status = 'PROCESSED', last_processed_at = CURRENT_TIMESTAMP WHERE id = :?id" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();

        from( "jms:queue:dhis2" ).id( "dhis2Route" )
            .setHeader( "originalPayload", simple( "${body}" ) )
            .onException( Exception.class )
            .to( "direct:dlq" )
            .end()
            .unmarshal()
            .json( Map.class )
            .enrich()
            .simple(
                "dhis2://get/resource?path=dataElements&filter=dataSetElements.dataSet.id:eq:${body['flow']['data_set_id']}&fields=code&client=#dhis2Client" )
            .aggregationStrategy( ( oldExchange, newExchange ) -> {
                oldExchange.getMessage().setHeader( "dataElementCodes",
                    jsonpath( "$.dataElements..code" ).evaluate( newExchange, List.class ) );
                return oldExchange;
            } )
            .setHeader( "period", periodExpression )
            .transform( datasonnet( "resource:classpath:dataValueSet.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .process( idSchemeProcessor )
            .log( LoggingLevel.INFO, LOGGER, "Saving data value set => ${body}" )
            .to( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" );

        from( "direct:dlq" ).setHeader( "errorMessage", rootExceptionMessageExpression )
            .setHeader( "payload", header( "originalPayload" ) )
            .setBody( simple(
                "INSERT INTO DEAD_LETTER_CHANNEL (payload, status, error_message) VALUES (:?payload, 'ERROR', :?errorMessage)" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" );
    }
}
