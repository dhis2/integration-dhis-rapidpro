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
package org.hisp.dhis.integration.rapidpro.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hisp.dhis.api.model.v2_36_11.DataSet;
import org.hisp.dhis.api.model.v2_36_11.OrganisationUnit;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class PrepareBroadcastProcessor implements Processor
{
    private static final int MAX_CONTACT_IDS = 100;

    @Override
    public void process( Exchange exchange )
        throws Exception
    {
        String orgUnitIdScheme = exchange.getProperty( "orgUnitIdScheme", String.class ).toLowerCase();
        List<String> broadcastContactIds = new ArrayList<>();
        List<String> row = exchange.getMessage().getBody( List.class );
        DataSet dataSet = exchange.getProperty( "dataSet", DataSet.class );
        Map<String, Object> contacts = exchange.getProperty( "contacts", Map.class );

        String orgUnitId = row.get( 0 );
        OrganisationUnit organisationUnit = dataSet.getOrganisationUnits().get().stream()
            .filter( ou -> ou.getId().get().equals( orgUnitId ) ).findFirst().get();
        List<Map<String, Object>> results = (List<Map<String, Object>>) contacts.get( "results" );
        for ( Map<String, Object> result : results )
        {
            if ( ((Map<String, String>) result.get( "fields" )).get( "dhis2_organisation_unit_id" )
                .equals( ((Optional<String>) organisationUnit.get( orgUnitIdScheme )).get() ) )
            {
                broadcastContactIds.add( (String) result.get( "uuid" ) );
            }
        }

        List<List<String>> batchedContactIds = batchContacts( broadcastContactIds );
        List<Map<String, Object>> payloads = new ArrayList<>();
        ResourceBundle resourceBundle = ResourceBundle.getBundle( "reminder" );
        String text = MessageFormat.format( resourceBundle.getString( "text" ), dataSet.getName().get() );
        for ( List<String> contactIds : batchedContactIds )
        {
            payloads.add( Map.of( "contacts", contactIds, "text", text ) );
        }

        exchange.getMessage().setBody( payloads );
    }

    protected List<List<String>> batchContacts( List<String> contactsIds )
    {
        return IntStream.rangeClosed( 0, (contactsIds.size() - 1) / MAX_CONTACT_IDS )
            .mapToObj( i -> contactsIds.subList( i * MAX_CONTACT_IDS,
                Math.min( (i + 1) * MAX_CONTACT_IDS, contactsIds.size() ) ) )
            .collect( Collectors.toList() );
    }
}
