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
import org.hisp.dhis.api.model.v40_0.User;
import org.hisp.dhis.integration.rapidpro.IsContactPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class SyncRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private IsContactPoint isContactPoint;

    @Value( "${org.unit.id.scheme}" )
    private String orgUnitIdScheme;

    @Override
    public void doConfigure()
    {
        from( "servlet:tasks/sync?muteException=true" )
            .precondition( "{{sync.rapidpro.contacts}}" )
            .removeHeaders( "*" )
            .to( "direct:sync" )
            .setHeader( Exchange.CONTENT_TYPE, constant( "application/json" ) )
            .setBody( constant( Map.of("status", "success", "data", "Synchronised RapidPro contacts with DHIS2 users") ) )
            .marshal().json();

        from( "quartz://sync?cron={{sync.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .precondition( "{{sync.rapidpro.contacts}}" )
            .to( "direct:sync" );

        from( "direct:sync" )
            .precondition( "{{sync.rapidpro.contacts}}" )
            .routeId( "Sync RapidPro Contacts" )
            .log( LoggingLevel.INFO, LOGGER, "Synchronising RapidPro contacts..." )
            .to( "direct:prepareRapidPro" )
            .setProperty( "orgUnitIdScheme", simple( "{{org.unit.id.scheme}}" ) )
            .toD( "dhis2://get/collection?path=users&arrayName=users&fields=id,firstName,surname,phoneNumber,telegram,whatsApp,twitter,facebookMessenger,organisationUnits[${exchangeProperty.orgUnitIdScheme.toLowerCase()}~rename(id)]&filter=organisationUnits.id:!null&client=#dhis2Client" )
            .split(body())
                .to( "direct:createOrUpdateContact" )
            .end()
            .log( LoggingLevel.INFO, LOGGER, "Completed synchronisation of RapidPro contacts with DHIS2 users" );

        from( "direct:createOrUpdateContact" )
            .convertBodyTo( User.class )
            .filter( isContactPoint )
            .setProperty( "dhis2UserId" ).groovy( "body.id.get()" )
            .setHeader( "urn", simple( "ext:${exchangeProperty.dhis2UserId}" ))
            .setHeader( "group", constant( "DHIS2" ) )
            .toV( "kamelet:hie-rapidpro-get-contacts-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}", null, "rapidProContact" )
            .removeHeader( "urn" )
            .removeHeader( "group" )
            .setHeader( "groups").groovy( "['DHIS2']" )
            .setHeader( "contactName" ).groovy( "body.firstName.get() + ' ' + body.surname.get()" )
            .setHeader( "phoneNumber" ).groovy( "body.phoneNumber.orElse(null)" )
            .setHeader( "telegram" ).groovy( "body.telegram.orElse(null)" )
            .setHeader( "whatsApp" ).groovy( "body.whatsApp.orElse(null)" )
            .setHeader( "facebookMessenger" ).groovy( "body.facebookMessenger.orElse(null)" )
            .setHeader( "twitterId" ).groovy( "body.twitter.orElse(null)" )
            .choice()
                .when( exchange -> !exchange.getVariable( "rapidProContact", null, Iterator.class ).hasNext() )
                    .log( LoggingLevel.DEBUG, LOGGER, "Creating RapidPro contact for DHIS2 user ${exchangeProperty.dhis2UserId}" )
                    .setHeader( "external", exchangeProperty( "dhis2UserId" ) )
                    .setHeader( "fields").groovy( "[dhis2_organisation_unit_id : body.organisationUnits.get()[0].id, dhis2_user_id : exchangeProperties.dhis2UserId]"  )
                    .to( "kamelet:hie-rapidpro-create-or-update-contact-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}&httpOkStatusRange=200-499" )
                .otherwise()
                    .log( LoggingLevel.DEBUG, LOGGER, "Updating RapidPro contact for DHIS2 user ${exchangeProperty.dhis2UserId}" )
                    .setHeader( "uuid" ).groovy( "variables.rapidProContact.iterator().next().uuid" )
                    .to( "kamelet:hie-rapidpro-create-or-update-contact-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}&httpOkStatusRange=200-499" )
            .end();

    }
}
