package org.hisp.dhis.integration.rapidpro;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.hisp.dhis.api.model.v40_0.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IsContactPoint implements Predicate
{
    @Override
    public boolean matches( Exchange exchange )
    {
        User dhis2User = exchange.getMessage().getBody( User.class );
        return isNotBlank( dhis2User.getPhoneNumber() ) || isNotBlank( dhis2User.getTelegram() ) || isNotBlank(
            dhis2User.getTwitter() ) || isNotBlank( dhis2User.getFacebookMessenger() ) || isNotBlank(
            dhis2User.getWhatsApp() );
    }

    private boolean isNotBlank( Optional<String> stringOptional )
    {
        return stringOptional.isPresent() && !stringOptional.get().isBlank();
    }
}
