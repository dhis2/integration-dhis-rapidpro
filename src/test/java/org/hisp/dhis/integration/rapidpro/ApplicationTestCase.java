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

import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationArguments;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class ApplicationTestCase
{
    public static class TerminateException extends RuntimeException
    {

    }

    @ParameterizedTest
    @ValueSource( strings = { "dhis2.api.pat", "dhis2.api.password", "rapidpro.api.token",
        "spring.security.user.password", "spring.datasource.password" } )
    public void testPostConstructTerminatesGivenGivenSecretInCommandLineArgument( String argumentName )
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertEquals( String.format(
                    "`%s` is not permitted in a command-line argument. Hint: set it in an environment variable or inside the application properties file",
                    argumentName ), shutdownMessage );
                throw new TerminateException();
            }
        };
        application.setTestConnectionOnStartUp( false );
        application.setApplicationArguments( new ApplicationArguments()
        {
            @Override
            public String[] getSourceArgs()
            {
                return new String[0];
            }

            @Override
            public Set<String> getOptionNames()
            {
                return null;
            }

            @Override
            public boolean containsOption( String name )
            {
                return false;
            }

            @Override
            public List<String> getOptionValues( String name )
            {
                if ( name.equals( argumentName ) )
                {
                    return List.of( "" );
                }
                else
                {
                    if ( ThreadLocalRandom.current().nextBoolean() )
                    {
                        return Collections.emptyList();
                    }
                    else
                    {
                        return null;
                    }
                }
            }

            @Override
            public List<String> getNonOptionArgs()
            {
                return null;
            }
        } );
        assertThrows( TerminateException.class, application::postConstruct );
    }

    @Test
    public void testDhis2Connection()
    {
        Application application = new Application();
        application.testDhis2Connection( Environment.DHIS2_CLIENT );
    }

    @Test
    public void testDhis2ConnectionGivenUnknownHost()
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertTrue( shutdownMessage.startsWith(
                    "Connection error during DHIS2 connection test. Are you sure that `dhis2.api.url` is set correctly? Hint: check your firewall settings. Error message => " ) );
                throw new TerminateException();
            }
        };
        assertThrows( TerminateException.class, () -> application.testDhis2Connection(
            Dhis2ClientBuilder.newClient( "http://dhis2.test/api", "admin", "district" ).build() ) );
    }

    @Test
    public void testDhis2ConnectionGivenIncorrectApiUrl()
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertTrue( shutdownMessage.startsWith(
                    "Unexpected JSON response during DHIS2 connection test: expecting system info version. Are you sure that `dhis2.api.url` is set correctly and the right version of DHIS is installed? JSON response => {" ) );
                throw new TerminateException();
            }
        };
        assertThrows( TerminateException.class, () -> application.testDhis2Connection(
            Dhis2ClientBuilder.newClient(
                String.format( "http://localhost:%s/anything", Environment.HTTPBIN_CONTAINER.getFirstMappedPort() ),
                "admin", "district" ).build() ) );
    }

    @Test
    public void testDhis2ConnectionGivenIncorrectCredentials()
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertTrue( shutdownMessage.startsWith(
                    "Unexpected HTTP response code during DHIS2 connection test. Are you sure that `dhis2.api.url` is set correctly and the credentials are valid? Hint: check your firewall settings. Error message => Response{protocol=http/1.1, code=401, message=, url=http://localhost:" ) );
                throw new TerminateException();
            }
        };
        assertThrows( TerminateException.class, () -> application.testDhis2Connection(
            Dhis2ClientBuilder.newClient( Environment.DHIS2_CLIENT.getApiUrl(), "admin", "foo" ).build() ) );
    }

    @Test
    public void testRapidProConnection()
        throws
        IOException
    {
        Application application = new Application();
        application.setRapidProApiUrl( Environment.RAPIDPRO_API_URL );
        application.setRapidProApiToken( Environment.RAPIDPRO_API_TOKEN );
        application.testRapidProConnection();
    }

    @Test
    public void testPostConstructTerminatesGivenMissingApiToken()
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertEquals( "Missing RapidPro API token. Are you sure that you set `rapidpro.api.token`?",
                    shutdownMessage );
                throw new TerminateException();
            }
        };
        application.setApplicationArguments( new ApplicationArguments()
        {
            @Override
            public String[] getSourceArgs()
            {
                return new String[0];
            }

            @Override
            public Set<String> getOptionNames()
            {
                return null;
            }

            @Override
            public boolean containsOption( String name )
            {
                return false;
            }

            @Override
            public List<String> getOptionValues( String name )
            {
                return null;
            }

            @Override
            public List<String> getNonOptionArgs()
            {
                return null;
            }
        } );
        application.setRapidProApiUrl( "http://rapidpro.test/api/v2" );
        assertThrows( TerminateException.class, application::postConstruct );
    }

    @Test
    public void testRapidProConnectionGivenIncorrectApiUrl()
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertTrue( shutdownMessage.startsWith(
                    "Unexpected JSON response during RapidPro connection test: expecting workspace UUID. Are you sure that `rapidpro.api.url` is set correctly and the right version of RapidPro is installed? JSON response => {" ) );
                throw new TerminateException();
            }
        };
        application.setRapidProApiUrl(
            String.format( "http://localhost:%s/anything", Environment.HTTPBIN_CONTAINER.getFirstMappedPort() ) );
        application.setRapidProApiToken( "98f3fe494b94742cf577f442e2cc175ae4f635a5" );
        assertThrows( TerminateException.class, application::testRapidProConnection );
    }

    @Test
    public void testRapidProConnectionGivenIncorrectApiToken()
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertEquals(
                    "Unexpected HTTP response code during RapidPro connection test. Are you sure that `rapidpro.api.url` is set correctly and the credentials are valid? Response code: 403. Response body: {\"detail\":\"Invalid token\"}",
                    shutdownMessage );
                throw new TerminateException();
            }
        };
        application.setRapidProApiUrl( Environment.RAPIDPRO_API_URL );
        application.setRapidProApiToken( "98f3fe494b94742cf577f442e2cc175ae4f635a5" );
        assertThrows( TerminateException.class, application::testRapidProConnection );
    }

    @Test
    public void testRapidProConnectionGivenUnknownHost()
    {
        Application application = new Application()
        {
            @Override
            protected void terminate( String shutdownMessage )
            {
                assertTrue( shutdownMessage.startsWith(
                    "Connection error during RapidPro connection test. Are you sure that `rapidpro.api.url` is set correctly? Hint: check your firewall settings. Error message => " ) );
                throw new TerminateException();
            }
        };
        application.setRapidProApiUrl( "http://rapidpro.test/api/v2" );
        application.setRapidProApiToken( Environment.RAPIDPRO_API_TOKEN );
        assertThrows( TerminateException.class, application::testRapidProConnection );
    }
}
