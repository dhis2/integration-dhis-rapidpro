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
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.hisp.dhis.api.model.v40_0.DataSet;
import org.hisp.dhis.integration.rapidpro.CompleteDataSetRegistrationFunction;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.ContactOrgUnitIdAggrStrategy;
import org.hisp.dhis.integration.rapidpro.expression.RootCauseExpr;
import org.hisp.dhis.integration.rapidpro.processor.CurrentPeriodCalculator;
import org.hisp.dhis.integration.rapidpro.processor.IdSchemeQueryParamSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

//@Component
public class DeliverEventRouteBuilder extends AbstractRouteBuilder
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
            "direct:failedEventDelivery" ).maximumRedeliveries( 3 ).useExponentialBackOff().useCollisionAvoidance()
            .allowRedeliveryWhileStopping( false );

        from( "timer://retryEvents?fixedRate=true&period=5000" )
            .routeId( "Retry Events" )
            .setBody( simple( "${properties:retry.event.dlc.select.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource" )
            .split().body()
                .setHeader( "id", simple( "${body['id']}" ) )
                .log( LoggingLevel.INFO, LOGGER, "Retrying row with ID ${header.id}" )
                .setHeader( "eventId", simple( "${body['event_id']}" ) )
                .setBody( simple( "${body['payload']}" ) )
                .to( "jms:queue:dhis2ProgramStageEvents?exchangePattern=InOnly" )
                .setBody( simple( "${properties:processed.event.dlc.update.{{spring.sql.init.platform}}}" ) )
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
            // TODO:
            //  - fetch program stage data element codes
            //  - compare payload data element codes with program stage data element codes
            //  - Transform to event payload (create datasonnet transformation)
            //  - migrate tables? possible: check if exist => rename.
            .marshal().json().transform().body( String.class );

        from( "direct:transmitEvent" )
            .routeId( "Transmit Report" )
            .errorHandler( errorHandlerDefinition )
            .log( LoggingLevel.INFO, LOGGER, "Saving event => ${body}" )
            .setHeader( "dhisRequest", simple( "${body}" ) )
            .toD( "dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client" )
            .setBody( (Function<Exchange, Object>) exchange -> exchange.getMessage().getBody( String.class ) )
            .setHeader( "dhisResponse", simple( "${body}" ) )
            .unmarshal().json()
            .choice()
            .when( simple( "${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'" ) )
                .to( "direct:completeEventDelivery" )
            .otherwise()
                .log( LoggingLevel.ERROR, LOGGER, "Import error from DHIS2 while saving event => ${body}" )
                .to( "direct:failedEventDelivery" )
            .end();

        from( "direct:failedEventDelivery" )
            .routeId( "Save Failed Event" )
            .setHeader( "errorMessage", rootCauseExpr )
            .setHeader( "payload", header( "originalPayload" ) )
            .setHeader( "eventId" ).ognl( "request.headers.orgUnitId" )
            .setBody( simple( "${properties:error.event.dlc.insert.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" );

        from( "direct:completeEventDelivery" )
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
                .to( "direct:failedEventDelivery" )
            .end();
    }
}
