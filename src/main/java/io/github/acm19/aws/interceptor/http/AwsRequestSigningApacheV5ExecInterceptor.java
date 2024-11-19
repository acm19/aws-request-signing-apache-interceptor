/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.IOException;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;

/**
 * An {@link ExecChainHandler} that signs requests for any AWS service
 * running in a specific region using an AWS {@link HttpSigner} and
 * {@link AwsCredentialsProvider}.
 */
public final class AwsRequestSigningApacheV5ExecInterceptor implements AsyncExecChainHandler {
    private final RequestSigner signer;

    /**
     * Creates an {@code AwsRequestSigningApacheV5ExecInterceptor} with the
     * ability to sign request for a specific service in a region and
     * defined credentials.
     *
     * @param service                service the client is connecting to
     * @param signer                 signer implementation.
     * @param awsCredentialsProvider source of AWS credentials for signing
     * @param region                 signing region
     */
    public AwsRequestSigningApacheV5ExecInterceptor(String service,
                                                HttpSigner<AwsCredentialsIdentity> signer,
                                                AwsCredentialsProvider awsCredentialsProvider,
                                                Region region) {
        this.signer = new RequestSigner(service, signer, awsCredentialsProvider, region);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(HttpRequest request,
                        AsyncEntityProducer entityProducer,
                        Scope scope, AsyncExecChain chain,
                        AsyncExecCallback callback) throws HttpException, IOException {
        chain.proceed(request, entityProducer, scope, callback);
    }
}
