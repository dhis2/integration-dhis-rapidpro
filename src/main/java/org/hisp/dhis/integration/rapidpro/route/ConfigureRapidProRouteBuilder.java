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

import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.POST;

@Component
public class ConfigureRapidProRouteBuilder extends AbstractRouteBuilder
{
    @Override
    protected void doConfigure()
    {
        from( "direct:prepareRapidPro" ).routeId( "prepareRapidProRoute" ).to( "direct:createFieldsRoute" )
            .to( "direct:createGroupRoute" );

        setUpCreateFieldsRoute();
        setUpCreateGroupRoute();
    }

    private void setUpCreateFieldsRoute()
    {
        from( "direct:createFieldsRoute" ).routeId( "createRapidProFieldsRoute" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/fields.json?key=dhis2_organisation_unit_id&httpMethod=GET" )
            .setProperty( "fieldCount", jsonpath( "$.results.length()" ) )
            .choice().when().simple( "${exchangeProperty.fieldCount} == 0" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 Organisation Unit ID fields in RapidPro..." )
                .setBody( constant( Map.of( "label", "DHIS2 Organisation Unit ID", "value_type", "text" ) ) ).marshal()
                .json().toD( "{{rapidpro.api.url}}/fields.json?httpMethod=POST" )
            .end()
            .toD( "{{rapidpro.api.url}}/fields.json?key=dhis2_user_id&httpMethod=GET" )
            .setProperty( "fieldCount", jsonpath( "$.results.length()" ) )
            .choice().when().simple( "${exchangeProperty.fieldCount} == 0" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 User ID field in RapidPro..." )
                .setBody( constant( Map.of( "label", "DHIS2 User ID", "value_type", "text" ) ) ).marshal().json()
                .toD( "{{rapidpro.api.url}}/fields.json?httpMethod=POST" )
            .end();
    }

    private void setUpCreateGroupRoute()
    {
        from( "direct:createGroupRoute" ).routeId( "createRapidProGroupRoute" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/groups.json?name=DHIS2&httpMethod=GET" )
            .setProperty( "groupCount", jsonpath( "$.results.length()" ) )
            .choice().when()
                .simple( "${exchangeProperty.groupCount} == 0" ).log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 group in RapidPro..." )
                .setBody( constant( Map.of( "name", "DHIS2" ) ) ).marshal()
                .json().toD( "{{rapidpro.api.url}}/groups.json?httpMethod=POST" ).setProperty( "groupUuid", jsonpath( "$.uuid" ) )
            .otherwise()
                .setProperty( "groupUuid", jsonpath( "$.results[0].uuid" ) );
    }
}
