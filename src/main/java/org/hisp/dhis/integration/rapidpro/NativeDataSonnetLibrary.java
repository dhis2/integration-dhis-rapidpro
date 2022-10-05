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

import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.MediaTypes;
import com.datasonnet.header.Header;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.Library;
import org.hisp.dhis.api.model.v2_36_11.CategoryOptionCombo;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import sjsonnet.Materializer;
import sjsonnet.Val;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

@Component
public class NativeDataSonnetLibrary extends Library
{
    private static final Logger LOGGER = LoggerFactory.getLogger( NativeDataSonnetLibrary.class );

    @Autowired
    @Lazy
    private Dhis2Client dhis2Client;

    @Override
    public String namespace()
    {
        return "native";
    }

    @Override
    public Map<String, Val.Func> functions( DataFormatService dataFormats, Header header )
    {
        Map<String, Val.Func> answer = new HashMap<>();
        answer.put( "logWarning", logWarningFn() );
        answer.put( "isCategoryOptionCombo", isCategoryOptionComboFn( dataFormats ) );
        answer.put( "formatResource", formatResource( dataFormats ) );

        return answer;
    }

    protected Val.Func formatResource( DataFormatService dataFormats )
    {
        return makeSimpleFunc(
            List.of( "key", "dataSetName" ), vals -> {
                ResourceBundle resourceBundle = ResourceBundle.getBundle( "reminder" );
                String resource = MessageFormat.format( resourceBundle.getString( ((Val.Str) vals.get( 0 )).value() ),
                    ((Val.Str) vals.get( 1 )).value() );

                return Materializer.reverse( dataFormats.mandatoryRead(
                    new DefaultDocument<>( resource, MediaTypes.APPLICATION_JAVA ) ) );
            } );
    }

    protected Val.Func logWarningFn()
    {
        return makeSimpleFunc(
            Collections.singletonList( "msg" ), vals -> {
                LOGGER.warn( ((Val.Str) vals.get( 0 )).value() );
                return null;
            } );
    }

    protected Val.Func isCategoryOptionComboFn( DataFormatService dataFormats )
    {
        return makeSimpleFunc(
            Collections.singletonList( "categoryComboOptionCode" ), vals -> {
                Iterable<CategoryOptionCombo> categoryOptionCombos = dhis2Client.get( "categoryOptionCombos" )
                    .withFilter( "code:eq:" + ((Val.Str) vals.get( 0 )).value() ).withoutPaging()
                    .transfer().returnAs( CategoryOptionCombo.class, "categoryOptionCombos" );
                boolean categoryOptionComboExists = categoryOptionCombos.iterator().hasNext();
                if ( !categoryOptionComboExists )
                {
                    LOGGER.warn(
                        "Ignoring category option combination because of unknown category option combination code '" + ((Val.Str) vals.get(
                            0 )).value() + "'. Hint: ensure that the RapidPro category matches the corresponding DHIS2 category option combination code" );
                }
                return Materializer.reverse( dataFormats.mandatoryRead(
                    new DefaultDocument<>( categoryOptionComboExists, MediaTypes.APPLICATION_JAVA ) ) );
            } );
    }

    @Override
    public Map<String, Val.Obj> modules( DataFormatService dataFormats, Header header )
    {
        return Collections.emptyMap();
    }

    @Override
    public Set<String> libsonnets()
    {
        return Collections.emptySet();
    }
}
