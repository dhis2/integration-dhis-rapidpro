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
package org.hisp.dhis.integration.rapidpro;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.POST;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Dhis2RapidProRouteBuilder extends RouteBuilder
{
    private static final Logger LOGGER = LoggerFactory.getLogger( Dhis2RapidProRouteBuilder.class );

    @Autowired
    private NewContactsProcessor newContactsProcessor;

    @Autowired
    private ModifyContactsProcessor modifyContactsProcessor;

    @Autowired
    private BodyIterableToListExpression bodyIterableToListExpression;

    @Override
    public void configure()
    {
        onException( HttpOperationFailedException.class )
            .log( LoggingLevel.ERROR, LOGGER,
                "HTTP response body => ${exchangeProperty.CamelExceptionCaught.responseBody}" )
            .process( exchange -> {
                throw (Exception) exchange.getProperty( Exchange.EXCEPTION_CAUGHT );
            } );

        setUpSyncRoute();
        setUpPrepareRapidProRoute();
        setUpCreateFieldsRoute();
        setUpCreateGroupRoute();
    }

    private void setUpSyncRoute()
    {
        from( "direct://sync" ).routeId( "synchroniseDhis2UsersRoute" ).to( "direct:prepareRapidPro" ).to(
            "dhis2://get/collection?path=users&fields=id,firstName,surname,phoneNumber,organisationUnits&filter=organisationUnits.id:!null:&itemType=org.hisp.dhis.api.v2_37_4.model.User&paging=false&client=#dhis2Client" )
            .setProperty( "dhis2Users", bodyIterableToListExpression )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .setHeader( "CamelHttpMethod", constant( GET ) )
            .toD( "{{rapidpro.api.url}}/contacts.json?group=${exchangeProperty.groupUuid}" ).unmarshal().json()
            .setProperty( "rapidProContacts", simple( "${body}" ) ).process( newContactsProcessor ).split().body()
            .transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .marshal().json().convertBodyTo( String.class ).setHeader( "CamelHttpMethod", constant( POST ) )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/contacts.json" ).end().process( modifyContactsProcessor ).split().body()
            .setHeader( "rapidProUuid", simple( "${body.getKey}" ) ).setBody( simple( "${body.getValue}" ) )
            .transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object",
                "application/x-java-object" ) )
            .marshal().json().convertBodyTo( String.class ).setHeader( "CamelHttpMethod", constant( POST ) )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/contacts.json?uuid=${header.rapidProUuid}" )
            .end();
    }

    private void setUpPrepareRapidProRoute()
    {
        from( "direct:prepareRapidPro" ).routeId( "prepareRapidProRoute" ).to( "direct:createFieldsRoute" )
            .to( "direct:createGroupRoute" );
    }

    private void setUpCreateFieldsRoute()
    {
        from( "direct:createFieldsRoute" ).routeId( "createRapidProFieldsRoute" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .setHeader( "CamelHttpMethod", constant( GET ) )
            .toD( "{{rapidpro.api.url}}/fields.json?key=dhis2_organisation_unit_id" )
            .setHeader( "fieldCount", jsonpath( "$.results.length()" ) ).choice().when()
            .simple( "${header.fieldCount} == 0" ).log( LoggingLevel.INFO, LOGGER, "Creating fields in RapidPro..." )
            .setHeader( "CamelHttpMethod", constant( POST ) )
            .setBody( constant( Map.of( "label", "DHIS2 Organisation Unit ID", "value_type", "text" ) ) ).marshal()
            .json().toD( "{{rapidpro.api.url}}/fields.json" )
            .setBody( constant( Map.of( "label", "DHIS2 User ID", "value_type", "text" ) ) ).marshal().json()
            .toD( "{{rapidpro.api.url}}/fields.json" )
            .endChoice();
    }

    private void setUpCreateGroupRoute()
    {
        from( "direct:createGroupRoute" ).routeId( "createRapidProGroupRoute" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .setHeader( "CamelHttpMethod", constant( GET ) ).toD( "{{rapidpro.api.url}}/groups.json?name=DHIS2" )
            .setHeader( "groupCount", jsonpath( "$.results.length()" ) ).choice().when()
            .simple( "${header.groupCount} == 0" ).log( LoggingLevel.INFO, LOGGER, "Creating group in RapidPro..." )
            .setHeader( "CamelHttpMethod", constant( POST ) ).setBody( constant( Map.of( "name", "DHIS2" ) ) ).marshal()
            .json().toD( "{{rapidpro.api.url}}/groups.json" ).setProperty( "groupUuid", jsonpath( "$.uuid" ) )
            .otherwise().setProperty( "groupUuid", jsonpath( "$.results[0].uuid" ) );
    }
}
