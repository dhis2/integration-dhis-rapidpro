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
import org.hisp.dhis.api.model.Page;
import org.hisp.dhis.api.model.Pager;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.AttributesAggrStrategy;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.ProgramStageEventsAggrStrategy;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.RapidProContactEnricherAggrStrategy;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.TrackedEntityIdAggrStrategy;
import org.hisp.dhis.integration.rapidpro.processor.FetchDueEventsQueryParamSetter;
import org.hisp.dhis.integration.rapidpro.processor.SetAttributesEndpointProcessor;
import org.hisp.dhis.integration.rapidpro.processor.SetProgramStagesPropertyProcessor;
import org.hisp.dhis.integration.sdk.internal.operation.page.PageIterable;
import org.jgroups.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FetchScheduledTrackerEventsRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private SetProgramStagesPropertyProcessor setProgramStagesPropertyProcessor;

    @Autowired
    private ProgramStageEventsAggrStrategy programStageEventsAggrStrategy;

    @Autowired
    private RapidProContactEnricherAggrStrategy rapidProContactEnricherAggrStrategy;

    @Autowired
    private FetchDueEventsQueryParamSetter fetchDueEventsQueryParamSetter;

    @Autowired
    private TrackedEntityIdAggrStrategy trackedEntityIdAggrStrategy;

    @Autowired
    private AttributesAggrStrategy attributesAggrStrategy;

    @Autowired
    private SetAttributesEndpointProcessor setAttributesEndpointProcessor;

    @Override
    protected void doConfigure()
        throws
        Exception
    {
        from( "servlet:tasks/syncEvents?muteException=true" )
            .precondition( "{{sync.dhis2.events.to.rapidpro.flows}}" )
            .removeHeaders( "*" )
            .to( "direct:fetchAndProcessEvents" )
            .setHeader( Exchange.CONTENT_TYPE, constant( "application/json" ) )
            .setBody( constant( Map.of( "status", "success", "data", "Fetched and enqueued due program stage events" ) ) )
            .marshal().json();

        from( "quartz://fetchDueEvents?cron={{sync.events.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .precondition( "{{sync.dhis2.events.to.rapidpro.flows}}" )
            .to( "direct:fetchAndProcessEvents" );

        from( "direct:fetchAndProcessEvents" )
            .routeId( "Fetch And Process Tracker Events" )
            .to( "direct:fetchDueEvents" )
            .split( simple( "${exchangeProperty.dueEvents}" ) )
                .marshal().json().transform().body( String.class )
                .setProperty( "eventPayload", simple( "${body}" ) )
                .unmarshal().json()
                .to( "direct:fetchAttributes" )
                .to("direct:createRapidProContact");

        from( "direct:fetchDueEvents" )
            .routeId( "Fetch Due Events" )
            .process( setProgramStagesPropertyProcessor )
            .split( simple( "${exchangeProperty.programStages}" ) ).aggregationStrategy( programStageEventsAggrStrategy )
                .setProperty( "programStage", simple( "${body}" ) )
                .process( fetchDueEventsQueryParamSetter )
                .toD( "dhis2://get/collection?path=tracker/events&paging=true&arrayName=instances&fields=enrollment,programStage,orgUnit,scheduledAt,occurredAt,event,status&client=#dhis2Client" )
            .end()
            .setProperty( "dueEvents", simple( "${body}" ) )
            .setProperty( "dueEventsCount", simple( "${body.size}" ) )
            .log( LoggingLevel.INFO, LOGGER, "Fetched ${exchangeProperty.dueEventsCount} due events from DHIS2" );

        from( "direct:fetchAttributes" )
            .routeId( "Fetch Attributes" )
            .enrich()
                .simple( "dhis2://get/resource?path=tracker/enrollments/${body[enrollment]}&fields=trackedEntity,attributes[code]&client=#dhis2Client" )
                .aggregationStrategy( trackedEntityIdAggrStrategy )
            .process( setAttributesEndpointProcessor )
            .enrich()
                .simple( "${exchangeProperty.attributesEndpoint}" )
                .aggregationStrategy( attributesAggrStrategy )
            .choice().when( simple( "${body[contactUrn]} == null" ) )
                .log( LoggingLevel.ERROR, LOGGER, "Error while fetching phone number attribute from DHIS2 enrollment ${body[enrollment]}. Hint: Be sure to set the 'dhis2.phone.number.attribute.code' config property." )
                .stop();

        from( "direct:createRapidProContact" )
            .routeId( "Create RapidPro Contact" )
            .log( LoggingLevel.INFO, LOGGER, "Synchronising Tracked Entity Instance with RapidPro contact..." )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .enrich().simple( "{{rapidpro.api.url}}/contacts.json?urn=${body[contactUrn]}&httpMethod=GET" )
            .aggregationStrategy( rapidProContactEnricherAggrStrategy )
            .setProperty( "originalPayload", simple( "${body}") )
            .choice()
                .when( simple ("${body[results].size()} > 0" ) )
                    .log( LoggingLevel.DEBUG, LOGGER, "RapidPro Contact already exists. No action needed." )
                .otherwise()
                    .log( LoggingLevel.DEBUG, LOGGER, "RapidPro Contact does not exist. Creating new contact...")
                    .transform(
                        datasonnet( "resource:classpath:trackedEntityContact.ds", Map.class, "application/x-java-object",
                            "application/x-java-object" ) )
                    .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
                    .marshal().json().convertBodyTo( String.class )
                    .toD( "{{rapidpro.api.url}}/contacts.json?httpMethod=POST&okStatusCodeRange=200-499" )
                    .choice().when( header( Exchange.HTTP_RESPONSE_CODE ).isNotEqualTo( "201" ) )
                        .log( LoggingLevel.WARN, LOGGER, "Unexpected status code when creating RapidPro contact for DHIS2 enrollment ${exchangeProperty.originalPayload[enrollment]} with contact URN ${exchangeProperty.originalPayload[contactUrn]} => HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
                    .end()
                .end()
            .setBody( simple( "${exchangeProperty.originalPayload}" ) )
            .end();


    }
}
