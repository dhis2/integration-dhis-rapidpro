package org.hisp.dhis.integration.rapidpro.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

public class JsonUtils
{
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Object> parseJsonStringToMap( String jsonPayload )
    {
        try
        {
            return objectMapper.readValue( jsonPayload, Map.class );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Error parsing JSON string to Map", e );
        }
    }

    public static String mapToJsonString( Map<String, Object> map )
    {
        try
        {
            return objectMapper.writeValueAsString( map );
        }
        catch ( JsonProcessingException e )
        {
            throw new RuntimeException( "Error converting Map to JSON string", e );
        }
    }
}
