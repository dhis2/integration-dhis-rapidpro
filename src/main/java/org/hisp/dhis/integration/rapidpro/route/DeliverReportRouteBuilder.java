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

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.hisp.dhis.integration.rapidpro.CompleteDataSetRegistrationFunction;
import org.hisp.dhis.integration.rapidpro.ContactOrgUnitIdAggrStrategy;
import org.hisp.dhis.integration.rapidpro.expression.RootCauseExpr;
import org.hisp.dhis.integration.rapidpro.processor.CurrentPeriodCalculator;
import org.hisp.dhis.integration.rapidpro.processor.IdSchemeQueryParamSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class DeliverReportRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private CurrentPeriodCalculator currentPeriodCalculator;

    @Autowired
    private RootCauseExpr rootCauseExpr;

    @Autowired
    private IdSchemeQueryParamSetter idSchemeQueryParamSetter;

    @Autowired
    private ContactOrgUnitIdAggrStrategy contactOrgUnitIdAggrStrategy;

    @Autowired
    private CompleteDataSetRegistrationFunction completeDataSetRegistrationFunction;

    @Override
    protected void doConfigure()
    {
        ErrorHandlerFactory errorHandlerDefinition = deadLetterChannel(
            "direct:dlq" ).maximumRedeliveries( 3 ).useExponentialBackOff().useCollisionAvoidance()
            .allowRedeliveryWhileStopping( false );

        from( "timer://retry?fixedRate=true&period=5000" )
            .routeId( "Retry Reports" )
            .setBody( simple( "${properties:retry.dlc.select.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource" )
            .split().body()
                .setHeader( "id", simple( "${body['id']}" ) )
                .log( LoggingLevel.INFO, LOGGER, "Retrying row with ID ${header.id}" )
                .setHeader( "dataSetCode", simple( "${body['data_set_code']}" ) )
                .setHeader( "reportPeriodOffset", simple( "${body['report_period_offset']}" ) )
                .setHeader( "orgUnitId", simple( "${body['organisation_unit_id']}" ) )
                .setBody( simple( "${body['payload']}" ) )
                .to( "jms:queue:dhis2?exchangePattern=InOnly" )
                .setBody( simple( "${properties:processed.dlc.update.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();

        from( "quartz://dhis2?cron={{report.delivery.schedule.expression}}" )
            .routeId( "Schedule Report Delivery" )
            .precondition( "'{{report.delivery.schedule.expression:}}' != ''" )
            .pollEnrich( "jms:queue:dhis2" )
            .to( "direct:deliverReport" );

        from( "jms:queue:dhis2" )
            .routeId( "Consume Report" )
            .precondition( "'{{report.delivery.schedule.expression:}}' == ''" )
            .to( "direct:deliverReport" );

        from( "direct:deliverReport" )
            .routeId( "Deliver Report" )
            .to( "direct:transformReport" )
            .to( "direct:transmitReport" );

        from( "direct:transformReport" )
            .routeId( "Transform Report" )
            .errorHandler( errorHandlerDefinition )
            .streamCaching()
            .setHeader( "originalPayload", simple( "${body}" ) )
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
                    .aggregationStrategy( contactOrgUnitIdAggrStrategy )
                .end()
                .removeHeader( "Authorization" )
            .end()
            .enrich( "direct:computePeriod", ( oldExchange, newExchange ) -> {
                oldExchange.getMessage().setHeader( "period", newExchange.getMessage().getBody() );
                return oldExchange;
            } )
            .transform( datasonnet( "resource:classpath:dataValueSet.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .process( idSchemeQueryParamSetter )
            .marshal().json().transform().body( String.class );

        from( "direct:transmitReport" )
            .routeId( "Transmit Report" )
            .errorHandler( errorHandlerDefinition )
            .log( LoggingLevel.INFO, LOGGER, "Saving data value set => ${body}" )
            .setHeader( "dhisRequest", simple( "${body}" ) )
            .toD( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" )
            .setBody( (Function<Exchange, Object>) exchange -> exchange.getMessage().getBody( String.class ) )
            .setHeader( "dhisResponse", simple( "${body}" ) )
            .unmarshal().json()
            .choice()
            .when( simple( "${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'" ) )
                .to( "direct:completeDataSetRegistration" )
            .otherwise()
                .log( LoggingLevel.ERROR, LOGGER, "Import error from DHIS2 while saving data value set => ${body}" )
                .to( "direct:dlq" )
            .end();

        from( "direct:dlq" )
            .routeId( "Save Failed Report" )
            .setHeader( "errorMessage", rootCauseExpr )
            .setHeader( "payload", header( "originalPayload" ) )
            .setHeader( "orgUnitId" ).ognl( "request.headers.orgUnitId" )
            .setHeader( "dataSetCode" ).ognl( "request.headers.dataSetCode" )
            .setBody( simple( "${properties:error.dlc.insert.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" );

        from( "direct:computePeriod" )
            .routeId( "Compute Period" )
            .toD( "dhis2://get/collection?path=dataSets&filter=code:eq:${headers['dataSetCode']}&fields=periodType&itemType=org.hisp.dhis.api.model.v2_38_1.DataSet&paging=false&client=#dhis2Client" )
            .process( currentPeriodCalculator );

        from( "direct:completeDataSetRegistration" )
            .setBody( completeDataSetRegistrationFunction )
            .toD( "dhis2://post/resource?path=completeDataSetRegistrations&inBody=resource&client=#dhis2Client" )
            .unmarshal().json()
            .choice()
            .when( simple( "${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'" ) )
                .setHeader( "rapidProPayload", header( "originalPayload" ) )
                .setBody( simple( "${properties:success.log.insert.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .otherwise()
                .log( LoggingLevel.ERROR, LOGGER, "Error from DHIS2 while completing data set registration => ${body}" )
                .to( "direct:dlq" )
            .end();
    }
}
