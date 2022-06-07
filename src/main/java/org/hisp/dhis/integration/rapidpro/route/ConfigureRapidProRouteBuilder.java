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
        throws Exception
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
