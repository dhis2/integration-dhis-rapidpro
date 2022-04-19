package org.hisp.dhis.integration.rapidpro;

import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.Document;
import com.datasonnet.document.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hisp.dhis.api.v2_37_4.model.User;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class NewContactsProcessor implements Processor
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule( new Jdk8Module() );

    @Override public void process( Exchange exchange )
        throws Exception
    {
        Set<Document<Map<String, Object>>> newDhis2Users = new HashSet<>();
        List<User> dhis2Users = exchange.getProperty( "dhis2Users", List.class );
        Map<String, Object> rapidProContacts = exchange.getProperty( "rapidProContacts", Map.class );
        List<Map<String, Object>> results = (List<Map<String, Object>>) rapidProContacts.get( "results" );

        for ( User dhis2User : dhis2Users )
        {
            Optional<Map<String, Object>> rapidProContact = results.stream().filter(
                c -> ((Map<String, Object>) c.get( "fields" )).get( "dhis2_user_id" )
                    .equals( dhis2User.getId().get() ) ).findFirst();

            if (rapidProContact.isEmpty()) {
                newDhis2Users.add( new DefaultDocument<>(OBJECT_MAPPER.convertValue( dhis2User, Map.class ), new MediaType( "application", "x-java-object") ) );
            }
        }

        exchange.getMessage().setBody( newDhis2Users );
    }
}
