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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.language.simple.SimpleLanguage;
import org.hisp.dhis.integration.rapidpro.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TrackedEntityIdAndSetAttributesEndpointAggrStrategy implements AggregationStrategy
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(
        TrackedEntityIdAndSetAttributesEndpointAggrStrategy.class );

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Exchange aggregate( Exchange original, Exchange resource )
    {
        Map<String, Object> originalBodyMap = original.getMessage().getBody( Map.class );
        Map<String, Object> resourceBodyMap = JsonUtils.parseJsonStringToMap(
            resource.getMessage().getBody( String.class ) );

        String trackedEntity = (String) resourceBodyMap.get( "trackedEntity" );
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) resourceBodyMap.get( "attributes" );
        boolean isAttributesEmpty = (attributes == null || attributes.isEmpty());
        String attributesEndpoint = isAttributesEmpty
            ? "dhis2://get/resource?path=tracker/trackedEntities/${body[trackedEntity]}&fields=attributes[attribute,code,value]&client=#dhis2Client"
            : "dhis2://get/resource?path=tracker/enrollments/${body[enrollment]}&fields=attributes[attribute,code,value]&client=#dhis2Client";
        originalBodyMap.put( "trackedEntity", trackedEntity );
        original.getIn().setBody( originalBodyMap );
        String evaluatedValue = SimpleLanguage.simple( attributesEndpoint ).evaluate( original, String.class );
        original.setProperty( "attributesEndpoint", evaluatedValue );
        return original;

    }
}
