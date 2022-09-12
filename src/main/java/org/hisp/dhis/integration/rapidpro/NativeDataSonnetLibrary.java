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

import com.datasonnet.header.Header;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.Library;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sjsonnet.Val;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NativeDataSonnetLibrary extends Library
{
    private static final Logger LOGGER = LoggerFactory.getLogger( NativeDataSonnetLibrary.class );

    @Override
    public String namespace()
    {
        return "native";
    }

    @Override
    public Map<String, Val.Func> functions( DataFormatService dataFormats, Header header )
    {
        Map<String, Val.Func> answer = new HashMap<>();
        answer.put( "logWarning", makeSimpleFunc(
            Collections.singletonList( "key" ), vals -> {
                LOGGER.warn( ((Val.Str) vals.get( 0 )).value() );
                return null;
            } ) );

        return answer;
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
