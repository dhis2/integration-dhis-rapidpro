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
