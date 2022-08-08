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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.hisp.dhis.api.model.v2_36_11.DataSet;
import org.hisp.dhis.integration.rapidpro.expression.CurrentPeriodProcessor;
import org.hisp.dhis.integration.rapidpro.expression.RootExceptionMessageExpression;
import org.hisp.dhis.integration.rapidpro.processor.SetIdSchemeQueryParamProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataValueSetRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private CurrentPeriodProcessor currentPeriodProcessor;

    @Autowired
    private RootExceptionMessageExpression rootExceptionMessageExpression;

    @Autowired
    private SetIdSchemeQueryParamProcessor setIdSchemeQueryParamProcessor;

    @Override
    protected void doConfigure()
    {
        from( "servlet:webhook?httpMethodRestrict=POST&muteException=true" )
            .to( "jms:queue:dhis2?exchangePattern=InOnly" )
            .setBody().simple( "${null}" );

        from( "timer://retry?fixedRate=true&period=5000" )
            .setBody( constant( "SELECT * FROM DEAD_LETTER_CHANNEL WHERE status = 'RETRY' LIMIT 100" ) )
            .to( "jdbc:dataSource" )
            .split().body()
                .setHeader( "id", simple( "${body['ID']}" ) )
                .log( LoggingLevel.INFO, LOGGER, "Retrying row with ID ${header.id}" )
                .setHeader( "dataSetId", simple( "${body['DATA_SET_ID']}" ) )
                .setHeader( "reportPeriodOffset", simple( "${body['REPORT_PERIOD_OFFSET']}" ) )
                .setBody( simple( "${body['PAYLOAD']}" ) )
                .to( "jms:queue:dhis2?exchangePattern=InOnly" )
                .setBody( constant( "UPDATE DEAD_LETTER_CHANNEL SET status = 'PROCESSED', last_processed_at = CURRENT_TIMESTAMP WHERE id = :?id" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();

        from( "jms:queue:dhis2" ).id( "dhis2Route" )
            .setHeader( "originalPayload", simple( "${body}" ) )
            .onException( Exception.class )
                .to( "direct:dlq" )
            .end()
            .unmarshal().json()
            .choice().when( header( "reportPeriodOffset" ).isNull() ).setHeader( "reportPeriodOffset", constant( -1 ) ).end()
            .enrich()
                .simple( "dhis2://get/resource?path=dataElements&filter=dataSetElements.dataSet.id:eq:${headers['dataSetId']}&fields=code&client=#dhis2Client" )
                .aggregationStrategy( ( oldExchange, newExchange ) -> {
                    oldExchange.getMessage().setHeader( "dataElementCodes",
                        jsonpath( "$.dataElements..code" ).evaluate( newExchange, List.class ) );
                    return oldExchange;
            } )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .enrich().simple( "{{rapidpro.api.url}}/contacts.json?uuid=${body[contact][uuid]}&httpMethod=GET" )
            .aggregationStrategy( ( oldExchange, newExchange ) -> {
                oldExchange.getMessage().setHeader( "orgUnitId",
                    jsonpath( "$.results[0].fields.dhis2_organisation_unit_id" ).evaluate( newExchange, String.class ) );
                return oldExchange;
            } )
            .enrich().simple( "{{rapidpro.api.url}}/contacts.json?uuid=${body[contact][uuid]}&httpMethod=GET" )
            .aggregationStrategy( ( oldExchange, newExchange ) -> {
                oldExchange.getMessage().setHeader( "orgUnitId",
                        jsonpath( "$.results[0].fields.dhis2_organisation_unit_id" ).evaluate( newExchange, String.class ) );
                return oldExchange;
            } )
            .enrich( "direct:computePeriod", ( oldExchange, newExchange ) -> {
                oldExchange.getMessage().setHeader( "period", newExchange.getMessage().getBody() );
                return oldExchange;
            } )
            .transform( datasonnet( "resource:classpath:dataValueSet.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .process( setIdSchemeQueryParamProcessor )
            .log( LoggingLevel.INFO, LOGGER, "Saving data value set => ${body}" )
            .to( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" );

        from( "direct:dlq" )
            .setHeader( "errorMessage", rootExceptionMessageExpression )
            .setHeader( "payload", header( "originalPayload" ) )
            .setBody( simple(
                "INSERT INTO DEAD_LETTER_CHANNEL (payload, data_set_id, report_period_offset, status, error_message) VALUES (:?payload, :?dataSetId, :?reportPeriodOffset, 'ERROR', :?errorMessage)" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" );

        from( "direct:computePeriod" )
            .toD( "dhis2://get/resource?path=dataSets/${headers['dataSetId']}&fields=periodType&client=#dhis2Client" )
            .unmarshal().json( DataSet.class ).process( currentPeriodProcessor );
    }
}
