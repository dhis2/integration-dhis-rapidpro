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
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.EventAggrStrategy;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.ProgramStageDataElementCodesAggrStrategy;
import org.hisp.dhis.integration.rapidpro.expression.RootCauseExpr;
import org.hisp.dhis.integration.rapidpro.processor.EventUpdateQueryParamSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Component
public class DeliverEventRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private RootCauseExpr rootCauseExpr;

    @Autowired
    private EventAggrStrategy eventAggrStrategy;

    @Autowired
    private ProgramStageDataElementCodesAggrStrategy programStageDataElementCodesAggrStrategy;

    @Autowired
    private EventUpdateQueryParamSetter eventUpdateQueryParamSetter;

    @Override
    protected void doConfigure()
    {
        ErrorHandlerFactory errorHandlerDefinition = deadLetterChannel(
            "direct:failedEventDelivery" ).maximumRedeliveries( 3 ).useExponentialBackOff().useCollisionAvoidance()
            .allowRedeliveryWhileStopping( false );

        from( "timer://retryEvents?fixedRate=true&period=5000" )
            .routeId( "Retry Events" )
            .setBody( simple( "${properties:event.retry.dlc.select.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource" )
            .split().body()
                .setHeader( "id", simple( "${body['id']}" ) )
                .log( LoggingLevel.INFO, LOGGER, "Retrying row with ID ${header.id}" )
                .setHeader( "eventId", simple( "${body['event_id']}" ) )
                .setBody( simple( "${body['payload']}" ) )
                .to( "jms:queue:dhis2ProgramStageEvents?exchangePattern=InOnly" )
                .setBody( simple( "${properties:event.processed.dlc.update.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();

        from( "jms:queue:dhis2ProgramStageEvents" )
            .routeId( "Deliver Event" )
            .to( "direct:transformEvent" )
            .to( "direct:transmitEvent" );

        from( "direct:transformEvent" )
            .routeId( "Transform Event" )
            .errorHandler( errorHandlerDefinition )
            .streamCaching()
            .setHeader( "originalPayload", simple( "${body}" ) )
            .unmarshal().json()
            .enrich()
                .simple( "dhis2://get/resource?path=tracker/events/${header.eventId}&fields=event,program,programStage,enrollment,orgUnit&client=#dhis2Client" )
                .aggregationStrategy( eventAggrStrategy )
            .enrich()
                .simple("dhis2://get/resource?path=programStages/${body[programStage]}&fields=programStageDataElements[dataElement[code]]&client=#dhis2Client")
                .aggregationStrategy( programStageDataElementCodesAggrStrategy )
            .transform( datasonnet( "resource:classpath:dhis2Event.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .process( eventUpdateQueryParamSetter )
            .marshal().json().transform().body( String.class );

        from( "direct:transmitEvent" )
            .routeId( "Transmit Event" )
            .errorHandler( errorHandlerDefinition )
            .log( LoggingLevel.INFO, LOGGER, "Updating program stage event => ${body}" )
            .setHeader( "dhisRequest", simple( "${body}" ) )
            .toD("dhis2://post/resource?path=tracker&inBody=resource&client=#dhis2Client")
            .setBody( (Function<Exchange, Object>) exchange -> exchange.getMessage().getBody( String.class ) )
            .setHeader( "dhisResponse", simple( "${body}" ) )
            .unmarshal().json()
            .choice()
                .when( simple( "${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'" ) )
                    .log( LoggingLevel.INFO, LOGGER, "Successfully updated event => ${header.eventId} in dhis2 with response => ${header.dhisResponse}" )
                    .setHeader( "rapidProPayload", header( "originalPayload" ) )
                    .setBody( simple( "${properties:event.success.log.insert.{{spring.sql.init.platform}}}" ) )
                    .to( "jdbc:dataSource?useHeadersAsParameters=true" )
                .otherwise()
                    .log(LoggingLevel.ERROR, LOGGER, "Import error from DHIS2 while saving program stage event => ${body}")
                    .to( "direct:failedEventDelivery" )
            .end();

        from( "direct:failedEventDelivery" )
            .routeId( "Save Failed Event" )
            .setHeader( "errorMessage", rootCauseExpr )
            .setHeader( "payload", header( "originalPayload" ) )
            .setHeader( "eventId" ).ognl( "request.headers.eventId" )
            .setBody( simple( "${properties:event.error.dlc.insert.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" );
    }
}
