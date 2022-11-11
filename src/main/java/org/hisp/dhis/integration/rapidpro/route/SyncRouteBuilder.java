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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.hisp.dhis.integration.rapidpro.expression.IterableReader;
import org.hisp.dhis.integration.rapidpro.processor.ExistingUserEnumerator;
import org.hisp.dhis.integration.rapidpro.processor.NewUserEnumerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SyncRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private NewUserEnumerator newUserEnumerator;

    @Autowired
    private IterableReader iterableReader;

    @Autowired
    private ExistingUserEnumerator existingUserEnumerator;

    @Value( "${org.unit.id.scheme}" )
    private String orgUnitIdScheme;

    @Override
    public void doConfigure()
    {
        from( "servlet:tasks/sync?muteException=true" )
            .precondition( "{{sync.rapidpro.contacts}}" )
            .removeHeaders( "*" )
            .to( "direct:sync" )
            .setHeader( "Content-Type", constant( "text/html" ) )
            .setBody( constant( "<html><body>Synchronised RapidPro contacts with DHIS2 users</body></html>" ) );

        from( "quartz://sync?cron={{sync.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .precondition( "{{sync.rapidpro.contacts}}" )
            .to( "direct:sync" );

        from( "direct:sync" )
            .precondition( "{{sync.rapidpro.contacts}}" )
            .routeId( "Sync RapidPro Contacts" )
            .log( LoggingLevel.INFO, LOGGER, "Synchronising RapidPro contacts..." )
            .to( "direct:prepareRapidPro" )
            .setProperty( "orgUnitIdScheme", simple( "{{org.unit.id.scheme}}" ) )
            .toD( "dhis2://get/collection?path=users&fields=id,firstName,surname,phoneNumber,organisationUnits[${exchangeProperty.orgUnitIdScheme.toLowerCase()}~rename(id)]&filter=phoneNumber:!null:&itemType=org.hisp.dhis.api.model.v2_36_11.User&paging=false&client=#dhis2Client" )
            .setProperty( "dhis2Users", iterableReader )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .setProperty( "nextContactsPageUrl", simple( "{{rapidpro.api.url}}/contacts.json?group=DHIS2" ) )
            .loopDoWhile( exchangeProperty( "nextContactsPageUrl" ).isNotNull() )
                .toD( "${exchangeProperty.nextContactsPageUrl}" ).unmarshal().json()
                .setProperty( "nextContactsPageUrl", simple( "${body[next]}" ) )
                .setProperty( "rapidProContacts", simple( "${body}" ) )
                .process( newUserEnumerator )
                .split().body()
                    .to( "direct:createContact" )
                .end()
                .process( existingUserEnumerator )
                .split().body()
                    .to( "direct:updateContact" )
                .end()
            .end()
            .log( LoggingLevel.INFO, LOGGER, "Completed synchronisation of RapidPro contacts with DHIS2 users" );

        from( "direct:createContact" )
            .transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object", "application/x-java-object" ) )
            .setProperty( "dhis2UserId", simple( "${body['fields']['dhis2_user_id']}" ) )
            .marshal().json().convertBodyTo( String.class )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .log( LoggingLevel.DEBUG, LOGGER, "Creating RapidPro contact => ${body}" )
            .toD( "{{rapidpro.api.url}}/contacts.json?httpMethod=POST&okStatusCodeRange=200-499" )
            .choice().when( header( Exchange.HTTP_RESPONSE_CODE ).isNotEqualTo( "201" ) )
                .log( LoggingLevel.WARN, LOGGER, "Unexpected status code when creating RapidPro contact for DHIS2 user ${exchangeProperty.dhis2UserId} => HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
            .end();

        from( "direct:updateContact" )
            .setProperty( "rapidProUuid", simple( "${body.getKey}" ) )
            .setBody( simple( "${body.getValue}" ) )
            .transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object", "application/x-java-object" ) )
            .marshal().json().convertBodyTo( String.class )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .log( LoggingLevel.DEBUG, LOGGER, "Updating RapidPro contact => ${body}" )
            .toD( "{{rapidpro.api.url}}/contacts.json?uuid=${exchangeProperty.rapidProUuid}&httpMethod=POST&okStatusCodeRange=200-499" )
            .choice().when( header( Exchange.HTTP_RESPONSE_CODE ).isNotEqualTo( "200" ) )
                .log( LoggingLevel.WARN, LOGGER, "Unexpected status code when updating RapidPro contact ${exchangeProperty.rapidProUuid} => HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
            .end();

    }
}
