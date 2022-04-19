package org.hisp.dhis.integration.rapidpro;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.POST;

@Component
public class Dhis2RapidProRouteBuilder extends RouteBuilder
{
    private static final Logger LOGGER = LoggerFactory.getLogger( Dhis2RapidProRouteBuilder.class );

    @Autowired
    private NewContactsProcessor newContactsProcessor;

    @Autowired
    private ModifyContactsProcessor modifyContactsProcessor;

    @Override
    public void configure()
    {
        onException( HttpOperationFailedException.class )
            .log( LoggingLevel.ERROR, LOGGER,
                "HTTP response body => ${exchangeProperty.CamelExceptionCaught.responseBody}" ).process( exchange -> {
                throw (Exception) exchange.getProperty( Exchange.EXCEPTION_CAUGHT );
            } );

        setUpSyncRoute();
        setUpPrepareRapidProRoute();
        setUpCreateFieldsRoute();
        setUpCreateGroupRoute();
    }

    private void setUpSyncRoute()
    {
        from( "direct://sync" ).
        to( "direct:prepareRapidPro" ).
        to("dhis2://get/collection?path=users&fields=id,firstName,surname,phoneNumber,organisationUnits&filter=organisationUnits.id:!null:&itemType=org.hisp.dhis.api.v2_37_4.model.User&paging=false&client=#dhis2Client" ).
        process( exchange -> exchange.getMessage().setBody( StreamSupport
            .stream(exchange.getMessage().getBody(Iterable.class).spliterator(), false)
            .collect( Collectors.toList()) ) ).
        setProperty( "dhis2Users", simple( "${body}" ) ).
        setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) ).
        setHeader( "CamelHttpMethod", constant( GET ) ).
        toD( "{{rapidpro.api.url}}/contacts.json?group=${exchangeProperty.groupUuid}" ).
        unmarshal().json().
        setProperty( "rapidProContacts", simple( "${body}" ) ).
            process( newContactsProcessor ).
        split().body().
            transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object", "application/x-java-object") ).
            marshal().json().convertBodyTo( String.class ).
            setHeader( "CamelHttpMethod", constant( POST ) ).
            setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) ).
            toD( "{{rapidpro.api.url}}/contacts.json" ).
        end().
        process( modifyContactsProcessor ).
        split().body().
            setHeader( "rapidProUuid", simple( "${body.getKey}" ) ).
            setBody(simple( "${body.getValue}" )).
            transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object", "application/x-java-object") ).
            marshal().json().convertBodyTo( String.class ).
            setHeader( "CamelHttpMethod", constant( POST ) ).
            setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) ).
            toD( "{{rapidpro.api.url}}/contacts.json?uuid=${header.rapidProUuid}" )
        .end();
    }

    private void setUpPrepareRapidProRoute()
    {
        from( "direct:prepareRapidPro" ).
            to( "direct:createFieldsRoute" ).
            to( "direct:createGroupRoute" );
    }

    private void setUpCreateFieldsRoute()
    {
        from( "direct:createFieldsRoute" ).
        setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) ).
        setHeader( "CamelHttpMethod", constant( GET ) ).
        toD( "{{rapidpro.api.url}}/fields.json?key=dhis2_organisation_unit_id" ).
        setHeader( "fieldCount", jsonpath( "$.results.length()" ) ).
        choice().
        when().simple( "${header.fieldCount} == 0" ).
            log( LoggingLevel.INFO, LOGGER, "Creating fields in RapidPro..." ).
            setHeader( "CamelHttpMethod", constant( POST ) ).
            setBody( constant( Map.of( "label", "DHIS2 Organisation Unit ID", "value_type", "text" ) ) ).
            marshal().json().
            toD( "{{rapidpro.api.url}}/fields.json" ).
            setBody( constant( Map.of( "label", "DHIS2 User ID", "value_type", "text" ) ) ).
            marshal().json().
            toD( "{{rapidpro.api.url}}/fields.json" )
        .endChoice();
}

    private void setUpCreateGroupRoute()
    {
        from( "direct:createGroupRoute" ).
        setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) ).
        setHeader( "CamelHttpMethod", constant( GET ) ).
        toD( "{{rapidpro.api.url}}/groups.json?name=DHIS2" ).
        setHeader( "groupCount", jsonpath( "$.results.length()" ) ).
        choice().
        when().simple( "${header.groupCount} == 0" ).
            log( LoggingLevel.INFO, LOGGER, "Creating group in RapidPro..." ).
            setHeader( "CamelHttpMethod", constant( POST ) ).
            setBody( constant( Map.of( "name", "DHIS2" ) ) ).
            marshal().json().
            toD( "{{rapidpro.api.url}}/groups.json" ).
            setProperty( "groupUuid", jsonpath( "$.uuid" ) ).
        otherwise().
            setProperty( "groupUuid", jsonpath( "$.results[0].uuid" ) );
    }
}
