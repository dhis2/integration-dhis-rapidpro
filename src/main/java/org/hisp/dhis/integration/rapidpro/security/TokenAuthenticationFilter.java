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
package org.hisp.dhis.integration.rapidpro.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenAuthenticationFilter extends OncePerRequestFilter
{
    private static final Pattern authorizationPattern = Pattern.compile( "^Token (?<token>[a-zA-Z0-9-._~+/]+=*)$",
        2 );

    private static final String BEARER_TOKEN_HEADER_NAME = "Authorization";

    private final String requiredToken;

    public TokenAuthenticationFilter( String requiredToken )
    {
        this.requiredToken = requiredToken;
    }

    @Override
    protected void doFilterInternal( HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain )
        throws
        ServletException,
        IOException
    {
        try
        {
            String token = resolveFromAuthorizationHeader( request );

            if ( !token.equals( requiredToken ) )
            {
                throw new BadCredentialsException( "Bad token" );
            }
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = UsernamePasswordAuthenticationToken.authenticated(
                "rapidpro", token, AuthorityUtils.NO_AUTHORITIES );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication( usernamePasswordAuthenticationToken );

            SecurityContextHolder.setContext( context );

            filterChain.doFilter( request, response );
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }

    }

    protected String resolveFromAuthorizationHeader( HttpServletRequest request )
    {
        String authorization = request.getHeader( this.BEARER_TOKEN_HEADER_NAME );
        if ( !StringUtils.startsWithIgnoreCase( authorization, "token" ) )
        {
            return null;
        }
        else
        {
            Matcher matcher = authorizationPattern.matcher( authorization );
            if ( !matcher.matches() )
            {
                BearerTokenError error = BearerTokenErrors.invalidToken( "Token is malformed" );
                throw new OAuth2AuthenticationException( error );
            }
            else
            {
                return matcher.group( "token" );
            }
        }
    }
}