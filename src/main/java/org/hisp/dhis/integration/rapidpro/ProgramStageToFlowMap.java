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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties( prefix = "dhis2.rapidpro" )
public class ProgramStageToFlowMap
{
    private Map<String, String> map = new HashMap<>();

    public Map<String, String> getMap()
    {
        return this.map;
    }

    public void setMap( Map<String, String> map )
    {
        this.map = map;
    }

    public void add( String programStageId, String flowUuid )
    {
        map.put( programStageId, flowUuid );
    }

    public void remove( String programStageId )
    {
        map.remove( programStageId );
    }

    public void clear()
    {
        map.clear();
    }

    public boolean flowUuidExists( String flowUuid )
    {
        return map.values().contains( flowUuid );
    }

    public String getFlowUuids( String programStageId )
    {
        return map.get( programStageId );
    }

    public String getFlowUuids( Map<String, Object> body )
    {
        return map.get( body.get( "programStage" ) );
    }

    public String getFlowUuids()
    {
        return String.join( ",", this.map.values() );
    }

    public void deleteFlows()
    {
        this.map.clear();
    }

    public String getProgramStage( String flowUuid )
    {
        for ( Map.Entry<String, String> entry : map.entrySet() )
        {
            if ( entry.getValue().equals( flowUuid ) )
            {
                return entry.getKey();
            }
        }
        return null;
    }

    public List<String> getAllProgramStageIds()
    {
        return new ArrayList<>( map.keySet() );
    }

}
