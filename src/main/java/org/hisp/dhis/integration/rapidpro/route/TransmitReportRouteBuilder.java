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
import org.hisp.dhis.integration.rapidpro.processor.CurrentPeriodCalculator;
import org.hisp.dhis.integration.rapidpro.expression.RootCauseExpr;
import org.hisp.dhis.integration.rapidpro.processor.IdSchemeQueryParamSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TransmitReportRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private CurrentPeriodCalculator currentPeriodCalculator;

    @Autowired
    private RootCauseExpr rootCauseExpr;

    @Autowired
    private IdSchemeQueryParamSetter idSchemeQueryParamSetter;

    @Override
    protected void doConfigure()
    {
        from( "timer://retry?fixedRate=true&period=5000" )
            .setBody( simple( "${properties:retry.dlc.select.{{spring.datasource.platform}}}" ) )
            .to( "jdbc:dataSource" )
            .split().body()
                .setHeader( "id", simple( "${body['ID']}" ) )
                .log( LoggingLevel.INFO, LOGGER, "Retrying row with ID ${header.id}" )
                .setHeader( "dataSetCode", simple( "${body['DATA_SET_CODE']}" ) )
                .setHeader( "reportPeriodOffset", simple( "${body['REPORT_PERIOD_OFFSET']}" ) )
                .setHeader( "orgUnitId", simple( "${body['ORGANISATION_UNIT_ID']}" ) )
                .setBody( simple( "${body['PAYLOAD']}" ) )
                .to( "jms:queue:dhis2?exchangePattern=InOnly" )
                .setBody( simple( "${properties:processed.dlc.update.{{spring.datasource.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();

        from( "quartz://dhis2?cron={{report.delivery.schedule.expression}}" )
            .precondition( "'{{report.delivery.schedule.expression:}}' != ''" )
            .pollEnrich("jms:queue:dhis2")
            .to( "direct:dhis2" );

        from( "jms:queue:dhis2" )
            .precondition( "'{{report.delivery.schedule.expression:}}' == ''" )
            .to( "direct:dhis2" );

        from( "direct:dhis2" )
            .id( "dhis2Route" )
            .setHeader( "originalPayload", simple( "${body}" ) )
            .onException( Exception.class )
                .to( "direct:dlq" )
            .end()
            .unmarshal().json()
            .choice().when( header( "reportPeriodOffset" ).isNull() )
                .setHeader( "reportPeriodOffset", constant( -1 ) )
            .end()
            .enrich()
                .simple( "dhis2://get/resource?path=dataElements&filter=dataSetElements.dataSet.code:eq:${headers['dataSetCode']}&fields=code&client=#dhis2Client" )
                .aggregationStrategy( ( oldExchange, newExchange ) -> {
                    oldExchange.getMessage().setHeader( "dataElementCodes",
                        jsonpath( "$.dataElements..code" ).evaluate( newExchange, List.class ) );
                    return oldExchange;
            } )
            .choice().when( header( "orgUnitId" ).isNull() )
                .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
                .enrich().simple( "{{rapidpro.api.url}}/contacts.json?uuid=${body[contact][uuid]}&httpMethod=GET" )
                    .aggregationStrategy( ( oldExchange, newExchange ) -> {
                        LOGGER.debug( String.format(
                            "Fetched contact %s => %s ", simple( "${body[contact][uuid]}" ).evaluate( oldExchange, String.class ),
                            newExchange.getMessage().getBody( String.class ) ) );
                        oldExchange.getMessage().setHeader( "orgUnitId",
                            jsonpath( "$.results[0].fields.dhis2_organisation_unit_id" ).evaluate( newExchange, String.class ) );
                        oldExchange.getMessage().removeHeader( "Authorization" );
                        return oldExchange;
                    } )
                .end()
            .end()
            .enrich( "direct:computePeriod", ( oldExchange, newExchange ) -> {
                oldExchange.getMessage().setHeader( "period", newExchange.getMessage().getBody() );
                return oldExchange;
            } )
            .transform( datasonnet( "resource:classpath:dataValueSet.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .process( idSchemeQueryParamSetter )
            .marshal().json().transform().body(String.class)
            .log( LoggingLevel.INFO, LOGGER, "Saving data value set => ${body}" )
            .toD( "{{report.destination.endpoint}}" );

        from( "direct:dlq" )
            .setHeader( "errorMessage", rootCauseExpr )
            .setHeader( "payload", header( "originalPayload" ) )
            .setHeader( "orgUnitId" ).ognl( "request.headers.orgUnitId == null ? null : header(orgUnitId)" )
            .setBody( simple( "${properties:error.dlc.insert.{{spring.datasource.platform}}}" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" );

        from( "direct:computePeriod" )
            .toD( "dhis2://get/collection?path=dataSets&filter=code:eq:${headers['dataSetCode']}&fields=periodType&itemType=org.hisp.dhis.api.model.v2_36_11.DataSet&paging=false&client=#dhis2Client" )
            .process( currentPeriodCalculator );
    }
}
