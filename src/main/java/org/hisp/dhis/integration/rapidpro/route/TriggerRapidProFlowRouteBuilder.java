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
import org.apache.camel.http.base.HttpOperationFailedException;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.RapidProContactEnricherAggrStrategy;
import org.hisp.dhis.integration.rapidpro.processor.GetFlowUuidProcessor;
import org.hisp.dhis.integration.rapidpro.processor.SetContactUuidProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TriggerRapidProFlowRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private RapidProContactEnricherAggrStrategy rapidProContactEnricherAggrStrategy;

    @Autowired
    private SetContactUuidProcessor setContactUuidProcessor;

    @Autowired
    private GetFlowUuidProcessor getFlowUuidProcessor;

    @Override
    protected void doConfigure()
        throws
        Exception
    {
        from( "jms:queue:events" )
            .routeId( "Consume Events" )
            .unmarshal().json()
            .to("direct:createRapidProContact")
            .to( "direct:triggerRapidProFlow" );

        from( "timer://retry?fixedRate=true&period=60000" )
            .routeId( "Retry Flow" )
            .setBody( simple( "${properties:retry.flow.select.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource" )
            .split().body()
                .setHeader( "id", simple( "${body['id']}" ) )
                .log( LoggingLevel.INFO, LOGGER, "Retrying flow with ID ${header.id}" )
                .setBody( simple( "${body['payload']}" ) )
                .log( LoggingLevel.INFO, LOGGER, "Retrying flow with body ${body}" )
                .unmarshal().json()
                .to( "direct:triggerRapidProFlow" )
                .setBody( simple( "${properties:processed.flow.update.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();

        from( "direct:triggerRapidProFlow" )
            .routeId( "Trigger RapidPro Flows" )
            .setProperty( "originalPayload", simple( "${body}" ) )
            .to( "direct:checkIfContactHasActiveFlowRun")
            .to("direct:transformToFlowStart")
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .doTry()
                .marshal().json().convertBodyTo( String.class )
                .toD( "{{rapidpro.api.url}}/flow_starts.json?httpMethod=POST" )
            .doCatch( HttpOperationFailedException.class )
                .log( LoggingLevel.ERROR, LOGGER,
                "Flow with UUID '${exchangeProperty.originalPayload[flowUuid]}' does not exist in RapidPro. Are you sure that you have configured the correct Flow UUID for Program Stage '${exchangeProperty.originalPayload[programStage]}'? " )
                .stop()
            .end()
            .choice().when( header( Exchange.HTTP_RESPONSE_CODE ).isNotEqualTo( "201" ) )
                .log( LoggingLevel.ERROR, LOGGER,
                    "Unexpected status code when triggering RapidPro flow run for Program Stage event with id ${exchangeProperty.originalPayload[event]} => HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
            .otherwise()
                .log(LoggingLevel.DEBUG, LOGGER, "Successfully triggered flow run for event with id => ${exchangeProperty.originalPayload[event]}");

        from( "direct:createRapidProContact" )
            .routeId( "Create RapidPro Contact" )
            .log( LoggingLevel.INFO, LOGGER, "Processing RapidPro contact for DHIS2 enrollment ${body[enrollment]}" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .enrich().simple( "{{rapidpro.api.url}}/contacts.json?urn=${body[contactUrn]}&httpMethod=GET" )
            .aggregationStrategy( rapidProContactEnricherAggrStrategy )
            .setProperty( "originalPayload", simple( "${body}" ) )
            .choice().when( simple( "${body[results].size()} > 0" ) )
                .log( LoggingLevel.DEBUG, LOGGER,
                    "RapidPro Contact already exists for DHIS2 enrollment ${exchangeProperty.originalPayload[enrollment]}. No action needed." )
                .setProperty( "contactUuid", simple( "${body[results][0][uuid]}" ) )
            .otherwise()
                .log( LoggingLevel.DEBUG, LOGGER,
                    "RapidPro Contact does not exist for DHIS2 enrollment ${exchangeProperty.originalPayload[enrollment]}. Creating new contact..." )
                .transform(
                    datasonnet( "resource:classpath:trackedEntityContact.ds", Map.class, "application/x-java-object",
                        "application/x-java-object" ) )
                .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
                .marshal().json().convertBodyTo( String.class )
                .toD( "{{rapidpro.api.url}}/contacts.json?httpMethod=POST&okStatusCodeRange=200-499" )
                .choice().when( header( Exchange.HTTP_RESPONSE_CODE ).isNotEqualTo( "201" ) )
                    .log( LoggingLevel.WARN, LOGGER,
                        "Unexpected status code when creating RapidPro contact for DHIS2 enrollment ${exchangeProperty.originalPayload[enrollment]} => HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
                    .stop()
                .end()
                .unmarshal().json()
                .setProperty( "contactUuid", simple( "${body[uuid]}" ) )
            .end()
            .setBody( simple( "${exchangeProperty.originalPayload}" ) )
            .process( setContactUuidProcessor )
            .log( LoggingLevel.DEBUG, LOGGER,
                "Processed RapidPro contact ${body[contactUuid]} for DHIS2 enrollment ${body[enrollment]} => HTTP ${header.CamelHttpResponseCode}." )
            .end();

        from("direct:checkIfContactHasActiveFlowRun")
            .routeId( "Check If RapidPro Contact Has Active Flow Run" )
            .setHeader( "Authorization", simple( "Token {{rapidpro.api.token}}" ) )
            .setHeader( Exchange.HTTP_METHOD, constant( "GET" ) )
            .toD( "{{rapidpro.api.url}}/runs.json?contact=${exchangeProperty.contactUuid}" ).id( "flowRunEndpoint" )
            .unmarshal().json()
            .choice().when().jsonpath( "$..results[?(@.exit_type == null)]", true )
                .log( LoggingLevel.INFO, LOGGER, "Pending RapidPro flow found for contact => ${exchangeProperty.contactUuid}. Saving flow to retry later..." )
                .to("direct:savePendingFlow")
                .stop()
            .otherwise()
                .log( LoggingLevel.DEBUG, LOGGER,
                    "No active flow runs found for contact => ${exchangeProperty.contactUuid}. Proceeding to trigger flow" );

        from( "direct:savePendingFlow" )
            .routeId( "Save Pending Flow" )
            .setBody( simple( "${exchangeProperty.originalPayload}" ) )
            .marshal().json().convertBodyTo( String.class )
            .log(LoggingLevel.DEBUG, LOGGER, "original payload: ${body}")
            .setHeader( "payload", simple( "${body}" ) )
            .log(LoggingLevel.DEBUG, LOGGER, "Header payload: ${header.payload}")
            .setBody( simple( "${properties:retry.flow.insert.{{spring.sql.init.platform}}}" ) )
            .to( "jdbc:dataSource?useHeadersAsParameters=true" );

        from( "direct:transformToFlowStart")
            .setBody( simple( "${exchangeProperty.originalPayload}" ) )
            .process( getFlowUuidProcessor )
            .transform( datasonnet( "resource:classpath:flowStart.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) );
    }
}

