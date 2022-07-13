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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataValueSetRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private PeriodExpression periodExpression;

    @Override
    protected void doConfigure()
    {
        from( "jetty:{{http.endpoint.uri:http://localhost:8081/rapidProConnector}}/dhis2queue?httpMethodRestrict=POST" )
            .removeHeaders( "*" )
            .to( "jms:queue:reports?exchangePattern=InOnly" );

        from( "jms:queue:reports" ).unmarshal().json( Map.class )
            .enrich().simple(
                "dhis2://get/resource?path=dataElements&filter=dataSetElements.dataSet.id:eq:${body['flow']['data_set_id']}&fields=code&client=#dhis2Client" )
            .aggregationStrategy( ( oldExchange, newExchange ) -> {
                oldExchange.getMessage().setHeader( "dataElementCodes",
                    jsonpath( "$.dataElements..code" ).evaluate( newExchange, List.class ) );
                return oldExchange;
            } )
            .setHeader( "period", periodExpression )
            .transform( datasonnet( "resource:classpath:dataValueSet.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .process( exchange -> exchange.getMessage()
                .setHeader( "CamelDhis2.queryParams", Map.of( "dataElementIdScheme", List.of( "CODE" ) ) ) )
            .log( LoggingLevel.INFO, LOGGER, "Saving data value set => ${body}" )
            .to( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" );
    }
}
