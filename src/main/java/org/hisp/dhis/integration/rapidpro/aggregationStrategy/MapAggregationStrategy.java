/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hisp.dhis.integration.rapidpro.aggregationStrategy;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapAggregationStrategy implements AggregationStrategy
{

    public Map<String, String> getValue( Exchange exchange )
    {
        return exchange.getMessage().getBody(Map.class);
    }

    public boolean isStoreAsBodyOnCompletion()
    {
        return true;
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public void onCompletion( Exchange exchange )
    {
        if ( exchange != null && isStoreAsBodyOnCompletion() )
        {
            Map<String, Set<String>> list = (Map<String, Set<String>>) exchange.removeProperty(
                ExchangePropertyKey.GROUPED_EXCHANGE );
            if ( list != null )
            {
                exchange.getIn().setBody( list );
            }
        }
    }

    @Override
    public Exchange aggregate( Exchange oldExchange, Exchange newExchange )
    {
        Map<String, Set<String>> map;

        if ( oldExchange == null )
        {
            map = getMap( newExchange );
        }
        else
        {
            map = getMap( oldExchange );
        }

        if ( newExchange != null )
        {
            Map<String, String> value = getValue( newExchange );
            if ( value != null )
            {
                for ( Map.Entry<String, String> entry : value.entrySet() )
                {
                    Set<String> set = map.getOrDefault( entry.getKey(), new HashSet<>() );
                    set.add( entry.getValue() );
                    map.put( entry.getKey(), set );
                }
            }
        }

        return oldExchange != null ? oldExchange : newExchange;
    }

    @SuppressWarnings( "unchecked" )
    private Map<String, Set<String>> getMap( Exchange exchange )
    {
        Map<String, Set<String>> map = exchange.getProperty( ExchangePropertyKey.GROUPED_EXCHANGE, Map.class );
        if ( map == null )
        {
            map = new HashMap<>();
            exchange.setProperty( ExchangePropertyKey.GROUPED_EXCHANGE, map );
        }
        return map;
    }

}
