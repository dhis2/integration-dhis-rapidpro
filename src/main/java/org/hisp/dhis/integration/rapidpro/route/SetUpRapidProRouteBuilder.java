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

@Component
public class SetUpRapidProRouteBuilder extends AbstractRouteBuilder
{
    @Override
    protected void doConfigure()
    {
        from( "direct:prepareRapidPro" ).routeId( "Set up RapidPro" ).to( "direct:createFieldsRoute" )
            .to( "direct:createGroupRoute" );

        setUpCreateFieldsRoute();
        setUpCreateGroupRoute();
    }

    private void setUpCreateFieldsRoute()
    {
        from( "direct:createFieldsRoute" ).routeId( "Create RapidPro Fields" )
            .setHeader( "key", constant( "dhis2_organisation_unit_id" ) )
            .to( "kamelet:hie-rapidpro-get-fields-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
            .removeHeaders( "*" )
            .choice().when().groovy( "!body.iterator().hasNext()" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 Organisation Unit ID fields in RapidPro..." )
                .setHeader( "label", constant( "DHIS2 Organisation Unit ID" ) )
                .setHeader( "type", constant( "text" ) )
                .to( "kamelet:hie-rapidpro-create-field-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
                .removeHeaders( "*" )
            .end()
            .setHeader( "key", constant( "dhis2_user_id" ) )
            .to( "kamelet:hie-rapidpro-get-fields-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
            .removeHeaders( "*" )
            .choice().when().groovy( "!body.iterator().hasNext()" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 User ID field in RapidPro..." )
                .setHeader( "label", constant( "DHIS2 User ID" ) )
                .setHeader( "type", constant( "text" ) )
                .to( "kamelet:hie-rapidpro-create-field-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
                .removeHeaders( "*" )
            .end();
    }

    private void setUpCreateGroupRoute()
    {
        from( "direct:createGroupRoute" ).routeId( "Create RapidPro Group" )
            .setHeader( "name", constant( "DHIS2" ) )
            .to( "kamelet:hie-rapidpro-get-groups-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
            .choice().when().groovy( "!body.iterator().hasNext()" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 group in RapidPro..." )
                .to( "kamelet:hie-rapidpro-create-group-sink?rapidProApiToken={{rapidpro.api.token}}&rapidProApiUrl={{rapidpro.api.url}}" )
                .setProperty( "groupUuid", simple( "$.uuid" ) )
            .otherwise()
                .setProperty( "groupUuid", simple( "$.uuid" ) );
    }
}
