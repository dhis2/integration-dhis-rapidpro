package org.hisp.dhis.integration.rapidpro.route;

import org.apache.camel.LoggingLevel;
import org.hisp.dhis.integration.rapidpro.expression.BodyIterableToListExpression;
import org.hisp.dhis.integration.rapidpro.processor.ModifyContactsProcessor;
import org.hisp.dhis.integration.rapidpro.processor.NewContactsProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.POST;

@Component
public class SyncRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private NewContactsProcessor newContactsProcessor;

    @Autowired
    private BodyIterableToListExpression bodyIterableToListExpression;

    @Autowired
    private ModifyContactsProcessor modifyContactsProcessor;

    @Override
    public void doConfigure()
        throws Exception
    {
        from( "jetty:http://localhost:8081/rapidProConnector/syncDhis2Users" )
            .removeHeaders( "*" )
            .to( "direct:sync" ).setBody(constant( Map.of("message", "Synchronised DHIS2 users with RapidPro") )).marshal().json();

        from( "direct://sync" ).routeId( "synchroniseDhis2UsersRoute" ).to( "direct:prepareRapidPro" ).to(
                "dhis2://get/collection?path=users&fields=id,firstName,surname,phoneNumber,organisationUnits&filter=phoneNumber:!null:&itemType=org.hisp.dhis.api.v2_37_6.model.User&paging=false&client=#dhis2Client" )
            .setProperty( "dhis2Users", bodyIterableToListExpression )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .setHeader( "CamelHttpMethod", constant( GET ) )
            .toD( "{{rapidpro.api.url}}/contacts.json?group=${exchangeProperty.groupUuid}" ).unmarshal().json()
            .setProperty( "rapidProContacts", simple( "${body}" ) ).process( newContactsProcessor )
            .split().body()
                .transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object",
                    "application/x-java-object" ) )
                .marshal().json().convertBodyTo( String.class ).setHeader( "CamelHttpMethod", constant( POST ) )
                .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
                .toD( "{{rapidpro.api.url}}/contacts.json" )
            .end()
            .process( modifyContactsProcessor )
            .split().body()
                .setHeader( "rapidProUuid", simple( "${body.getKey}" ) ).setBody( simple( "${body.getValue}" ) )
                .transform( datasonnet( "resource:classpath:contact.ds", Map.class, "application/x-java-object",
                    "application/x-java-object" ) )
                .marshal().json().convertBodyTo( String.class ).setHeader( "CamelHttpMethod", constant( POST ) )
                .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
                .toD( "{{rapidpro.api.url}}/contacts.json?uuid=${header.rapidProUuid}" )
            .end()
            .log( LoggingLevel.INFO, LOGGER, "Completed synchronisation of DHIS2 users with RapidPro" );
    }
}
