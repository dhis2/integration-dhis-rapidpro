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
import org.hisp.dhis.api.model.v2_36_11.ListGrid;
import org.hisp.dhis.integration.rapidpro.expression.BodyIterableToListExpression;
import org.hisp.dhis.integration.rapidpro.processor.PrepareBroadcastProcessor;
import org.hisp.dhis.integration.rapidpro.processor.SetReportRateQueryParamProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReminderRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private SetReportRateQueryParamProcessor setReportRateQueryParamProcessor;

    @Autowired
    private PrepareBroadcastProcessor prepareBroadcastProcessor;

    @Autowired
    private BodyIterableToListExpression bodyIterableToListExpression;

    @Override
    protected void doConfigure()
    {
        from( "servlet:reminders?muteException=true" )
            .removeHeaders( "*" )
            .to( "direct:reminders" )
            .setHeader( "Content-Type", constant( "text/html" ) )
            .setBody( constant( "<html><body>Sent reminders of overdue reports</body></html>" ) );

        from( "quartz://reminders?cron={{reminder.schedule.expression:0 0 9 ? * *}}" )
            .to( "direct:reminders" );

        from( "direct:reminders" )
            .log( LoggingLevel.INFO, LOGGER, "Reminding RapidPro contacts of overdue reports..." )
            .setProperty( "orgUnitIdScheme", simple( "{{org.unit.id.scheme}}" ) )
            .choice().when( simple( "{{sync.rapidpro.contacts}} == true" ) )
                .to( "direct:sync" )
            .end()
            .enrich("direct:fetchContacts").setProperty( "contacts", simple( "${body}" ) )
            .split( simple( "{{reminder.data.set.codes:}}" ), "," )
                .setProperty( "dataSetCode", simple( "${body}" ) )
                .to( "direct:fetchDataSet" )
                .choice()
                    .when( body().isNull() )
                        .log( LoggingLevel.WARN, LOGGER, "Cannot remind contacts for unknown data set code '${exchangeProperty.dataSetCode}'" )
                    .otherwise()
                        .setProperty( "dataSet", simple(  "${body}" ) )
                        .to( "direct:fetchReportRate" )
                        .split( simple( "${body.rows.get}" ) )
                            .choice()
                                .when( simple( "${body[4]} != '100.0'" ) )
                                    .process( prepareBroadcastProcessor )
                                    .split(simple(  "${body}" ))
                                    .to( "direct:sendBroadcast" )
                            .end()
                        .end()
                    .end()
                .end()
            .end();

        from( "direct:fetchDataSet" )
            .toD( "dhis2://get/collection?path=dataSets&filter=code:eq:${body}&fields=id,name,periodType,organisationUnits[id,${exchangeProperty.orgUnitIdScheme.toLowerCase()}]&itemType=org.hisp.dhis.api.model.v2_36_11.DataSet&paging=false&client=#dhis2Client" )
            .transform( bodyIterableToListExpression )
            .choice()
                .when( simple( "${body.size()} > 0" ) )
                    .transform( simple( "${body[0]}" ) )
                .otherwise()
                    .setBody(simple( "${null}" ))
            .end();

        from( "direct:fetchContacts" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/contacts.json?group=DHIS2&httpMethod=GET" ).unmarshal().json();

        from( "direct:fetchReportRate" )
            .process( setReportRateQueryParamProcessor )
            .to( "dhis2://get/resource?path=analytics&client=#dhis2Client" )
            .unmarshal().json( ListGrid.class );

        from( "direct:sendBroadcast" )
            .marshal().json()
            .removeHeaders( "*" )
            .setHeader( "Content-Type", constant( "application/json" ) )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/broadcasts.json?httpMethod=POST" )
            .log( LoggingLevel.INFO, LOGGER, "Overdue report reminder sent => ${body}" );
    }
}
