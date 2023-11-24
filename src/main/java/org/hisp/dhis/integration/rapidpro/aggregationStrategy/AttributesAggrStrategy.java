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
package org.hisp.dhis.integration.rapidpro.aggregationStrategy;

import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AttributesAggrStrategy extends AbstractAggregationStrategy

{
    @Override
    public Exchange doAggregate( Exchange original, Exchange resource )
        throws
        Exception
    {
        String phoneNumberAttributeCode = original.getContext()
            .resolvePropertyPlaceholders( "{{dhis2.phone.number.attribute.code}}" );
        String givenNameAttributeCode = original.getContext()
            .resolvePropertyPlaceholders( "{{dhis2.given.name.attribute.code}}" );
        String rapidProContactScheme = original.getContext()
            .resolvePropertyPlaceholders( "{{rapidpro.contact.scheme}}" );
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) objectMapper.readValue(
            resource.getMessage().getBody( String.class ), Map.class ).get( "attributes" );
        Map<String, Object> originalBodyMap = original.getMessage().getBody( Map.class );
        for ( Map<String, Object> attribute : attributes )
        {
            String code = (String) attribute.get( "code" );
            if ( phoneNumberAttributeCode.equals( code ) )
            {
                originalBodyMap.put( "contactUrn", rapidProContactScheme + ":" + attribute.get( "value" ) );
            }
            else if ( givenNameAttributeCode.equals( code ) )
            {
                originalBodyMap.put( "givenName", attribute.get( "value" ) );
            }
        }
        original.getIn().setBody( originalBodyMap );
        return original;

    }
}
