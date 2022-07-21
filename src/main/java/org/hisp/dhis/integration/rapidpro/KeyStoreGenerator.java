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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class KeyStoreGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger( KeyStoreGenerator.class );

    public void generate()
        throws NoSuchAlgorithmException, NoSuchProviderException, OperatorCreationException, CertificateException,
        KeyStoreException, IOException
    {
        if ( !new File( "dhis2rapidpro" ).exists() )
        {
            LOGGER.info( "Generating key store..." );

            if ( Security.getProvider( BouncyCastleProvider.PROVIDER_NAME ) == null )
            {
                Security.insertProviderAt( new BouncyCastleProvider(), 2 );
            }

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( "RSA", "BC" );
            keyPairGenerator.initialize( 1024, new SecureRandom() );

            java.security.KeyPair keyPair = keyPairGenerator.generateKeyPair();

            X500Name x500Name = new X500Name( "CN=dhis-to-rapidpro, O=HISP Centre, L=Oslo, C=NO" );
            SubjectPublicKeyInfo pubKeyInfo = SubjectPublicKeyInfo.getInstance( keyPair.getPublic().getEncoded() );
            final Date start = new Date();
            final Date until = Date.from(
                LocalDate.now().plus( 100, ChronoUnit.YEARS ).atStartOfDay().toInstant( ZoneOffset.UTC ) );
            final X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder( x500Name,
                new BigInteger( 10, new SecureRandom() ), start, until, x500Name, pubKeyInfo
            );
            ContentSigner contentSigner = new JcaContentSignerBuilder( "SHA256WithRSA" ).build( keyPair.getPrivate() );

            Certificate certificate = new JcaX509CertificateConverter().setProvider( new BouncyCastleProvider() )
                .getCertificate( certificateBuilder.build( contentSigner ) );

            KeyStore keyStore = KeyStore.getInstance( "JKS" );
            keyStore.load( null, null );
            keyStore.setKeyEntry( "dhis2rapidpro", keyPair.getPrivate(), "secret".toCharArray(),
                new Certificate[] { certificate } );
            keyStore.store( new FileOutputStream( "tls.jks" ), "secret".toCharArray() );

            LOGGER.info( "Key store generated at " + new File( "tls.jks" ).toURI().toURL().toExternalForm() );
        }
        else
        {
            LOGGER.info( "Re-using existing key store at " + new File( "tls.jks" ).toURI().toURL().toExternalForm() );
        }
    }
}
