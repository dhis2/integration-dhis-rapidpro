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

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.hisp.dhis.integration.rapidpro.ProgramStageToFlowMap;
import org.hisp.dhis.integration.rapidpro.expression.LastRunCalculator;
import org.hisp.dhis.integration.rapidpro.expression.LastRunAtColumnReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PullRapidProFlowsRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private LastRunCalculator lastRunCalculator;

    @Autowired
    private LastRunAtColumnReader lastRunAtColumnReader;

    @Autowired
    private ProgramStageToFlowMap programStageToFlowMap;

    @Override
    protected void doConfigure()
    {
        from( "servlet:tasks/scan?muteException=true" )
            .removeHeaders( "*" )
            .to( "direct:pull" )
            .setHeader( Exchange.CONTENT_TYPE, constant( "application/json" ) )
            .setBody( constant( Map.of("status", "success", "data", "Scanned RapidPro flow runs") ) )
            .marshal().json();

        from( "quartz://pull?cron={{scan.reports.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .to( "direct:pull" );

        from( "direct:pull" )
            .routeId( "Scan RapidPro Flows" )
            .streamCaching()
            .process( exchange -> {
                exchange.setProperty( "flowUuids",
                    String.join( ",", programStageToFlowMap.getFlowUuids(),
                        exchange.getContext().resolvePropertyPlaceholders( "{{rapidpro.flow.uuids:}}" ) ) );
            } )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .split( simple( "${exchangeProperty.flowUuids}" ), "," )
                .setHeader( "flowUuid", simple( "${body}" ) )
                .setBody( simple( "${properties:last.run.select.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
                .setProperty( "lastRunAt", lastRunAtColumnReader )
                .setProperty( "nextRunsPageUrl", simple( "{{rapidpro.api.url}}/runs.json?flow=${header.flowUuid}&after=${exchangeProperty.lastRunAt}&reverse=true" ) )
                .setHeader( "newLastRunAt" ).ognl( "@java.sql.Timestamp@from(@java.time.Instant@now())" )
                .loopDoWhile( exchangeProperty( "nextRunsPageUrl" ).isNotNull() )
                    .toD( "${exchangeProperty.nextRunsPageUrl}" )
                    .log( LoggingLevel.DEBUG, LOGGER, "Fetched flow runs from ${exchangeProperty.nextRunsPageUrl} => ${body}" )
                    .unmarshal().json()
                    .setProperty( "nextRunsPageUrl", simple( "${body[next]}" ) )
                    .setHeader( "newLastRunAt", lastRunCalculator )
                    .split( simple( "${body[results]}" ) )
                        .filter( simple( "${body[exited_on]} != null && ${body[exit_type]} == 'completed'" ) )
                            .choice()
                                .when().simple("${body[values][data_set_code]} != null && ${body[values][event_id]} == null" )
                                    .to("direct:queueAggregateReport")
                                .when().simple( "${body[values][data_set_code]} == null && ${body[values][event_id]} != null")
                                    .to("direct:queueProgramStageEvent")
                                .otherwise()
                                    .log( LoggingLevel.ERROR, LOGGER,
                                        "Cannot process flow run for flow definition ${header.flowUuid} because one of the required flow results is missing. Hint: for aggregate data reports, save the data set code to a flow result named 'data_set_code' in RapidPro. For program stage events, save the value '@trigger.params.eventId' to a flow result named 'event_id'  in RapidPro." )
                                .end()
                    .end()
                .end()
                .setBody( simple( "${properties:last.run.upsert.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();

        from( "direct:queueAggregateReport" )
            .routeId("Queue Aggregate Report")
            .setHeader( "dataSetCode", simple( "${body[values][data_set_code][value]}" ) )
            .setHeader( "orgUnitId" ).ognl(
                "request.body['values']['org_unit_id'] == null ? null : request.body['values']['org_unit_id']['value']" )
            .setHeader( "reportPeriodOffset" ).ognl(
                "request.body['values']['report_period_offset'] == null ? null : request.body['values']['report_period_offset']['value']" )
            .transform( datasonnet( "resource:classpath:webhook.ds", String.class, "application/x-java-object",
                "application/json" ) )
            .to( "jms:queue:dhis2AggregateReports?exchangePattern=InOnly" )
            .log( LoggingLevel.DEBUG, LOGGER,
                "Enqueued aggregate report flow run [data set code = ${header.dataSetCode}, report period offset = ${header.reportPeriodOffset}, content = ${body}]" );

        from( "direct:queueProgramStageEvent" )
            .routeId( "Queue Program Stage Event" )
            .setHeader( "eventId", simple( "${body[values][event_id][value]}" ) )
            .transform( datasonnet( "resource:classpath:webhook.ds", String.class, "application/x-java-object",
                "application/json" ) )
            .to( "jms:queue:dhis2ProgramStageEvents?exchangePattern=InOnly" )
            .log( LoggingLevel.DEBUG, LOGGER, "Enqueued program stage event flow run [event Id = ${header.eventId}]" );
    }
}
