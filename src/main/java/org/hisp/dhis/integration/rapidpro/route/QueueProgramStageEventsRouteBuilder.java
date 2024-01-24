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
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.AttributesAggrStrategy;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.ProgramStageEventsAggrStrategy;
import org.hisp.dhis.integration.rapidpro.aggregationStrategy.TrackedEntityIdAggrStrategy;
import org.hisp.dhis.integration.rapidpro.processor.EventStatusUpdateProcessor;
import org.hisp.dhis.integration.rapidpro.processor.FetchDueEventsQueryParamSetter;
import org.hisp.dhis.integration.rapidpro.processor.SetAttributesEndpointProcessor;
import org.hisp.dhis.integration.rapidpro.processor.SetProgramStagesPropertyProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueueProgramStageEventsRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private SetProgramStagesPropertyProcessor setProgramStagesPropertyProcessor;

    @Autowired
    private ProgramStageEventsAggrStrategy programStageEventsAggrStrategy;

    @Autowired
    private FetchDueEventsQueryParamSetter fetchDueEventsQueryParamSetter;

    @Autowired
    private TrackedEntityIdAggrStrategy trackedEntityIdAggrStrategy;

    @Autowired
    private AttributesAggrStrategy attributesAggrStrategy;

    @Autowired
    private SetAttributesEndpointProcessor setAttributesEndpointProcessor;

    @Autowired
    private EventStatusUpdateProcessor eventStatusUpdateProcessor;

    @Override
    protected void doConfigure()
        throws
        Exception
    {
        from( "servlet:tasks/syncEvents?muteException=true" )
            .precondition( "{{sync.dhis2.events.to.rapidpro.flows}}" )
            .removeHeaders( "*" )
            .to( "direct:queueEvents" )
            .setHeader( Exchange.CONTENT_TYPE, constant( "application/json" ) )
            .setBody( constant( Map.of( "status", "success", "data", "Fetched and enqueued due program stage events" ) ) )
            .marshal().json();

        from( "quartz://fetchDueEvents?cron={{sync.events.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .precondition( "{{sync.dhis2.events.to.rapidpro.flows}}" )
            .to( "direct:queueEvents" );

        from("direct:queueEvents")
            .routeId( "Queue Program Stage Events" )
            .to( "direct:fetchDueEvents" )
            .split( simple( "${exchangeProperty.dueEvents}" ) )
                .setProperty( "eventPayload", simple( "${body}" ) )
                .to( "direct:fetchAttributes" )
                .transform( datasonnet( "resource:classpath:event.ds", String.class, "application/x-java-object", "application/json" ) )
                .to( "jms:queue:events?exchangePattern=InOnly" )
                .unmarshal().json()
                .log( LoggingLevel.DEBUG, LOGGER, "Enqueued event [eventId => ${body[event]}, programStage => ${body[programStage]}]" )
                .to( "direct:updateDhisProgramStageEventStatus" );

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
            .log( LoggingLevel.INFO, LOGGER, "Fetched ${body.size} due events from DHIS2" );

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

        from("direct:updateDhisProgramStageEventStatus")
            .routeId( "Update DHIS Program Stage Event Status" )
            .process( eventStatusUpdateProcessor )
            .marshal().json().convertBodyTo( String.class )
            .toD( "dhis2://post/resource?path=tracker&inBody=resource&client=#dhis2Client" )
            .unmarshal().json()
            .choice().when( simple( "${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'" ) )
                .log( LoggingLevel.DEBUG, LOGGER, "Successfully updated DHIS program stage event status for event with ID => ${exchangeProperty.eventPayload['event']}" )
            .otherwise()
                .log( LoggingLevel.ERROR, LOGGER, "Unexpected status code when updating the dhis program stage event status for event with ID => ${exchangeProperty.eventPayload['event']}. HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
            .end();
    }
}
