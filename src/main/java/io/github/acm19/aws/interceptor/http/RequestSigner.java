/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.signer.Signer;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;

class RequestSigner {
    /**
     * A service the client is connecting to.
     */
    private final String service;
    /**
     * A signer implementation.
     */
    private final Signer signer;
    /**
     * The source of AWS credentials for signing.
     */
    private final AwsCredentialsProvider awsCredentialsProvider;
    /**
     * The signing region.
     */
    private final Region region;

    /**
     *
     * @param service
     * @param signer
     * @param awsCredentialsProvider
     * @param region
     */
    RequestSigner(final String service,
                         final Signer signer,
                         final AwsCredentialsProvider awsCredentialsProvider,
                         final Region region) {
        this.service = service;
        this.signer = signer;
        this.awsCredentialsProvider = awsCredentialsProvider;
        this.region = Objects.requireNonNull(region);
    }

    /**
     * Signs the {@code request} using
     * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html">
     * AWS Signature Version 4</a>.
     *
     * @param request to be signed
     * @return signed request
     * @see Signer#sign
     */
    SdkHttpFullRequest signRequest(final SdkHttpFullRequest request) {
        ExecutionAttributes attributes = new ExecutionAttributes();
        attributes.putAttribute(AwsSignerExecutionAttribute.AWS_CREDENTIALS,
                                awsCredentialsProvider.resolveCredentials());
        attributes.putAttribute(AwsSignerExecutionAttribute.SERVICE_SIGNING_NAME, service);
        attributes.putAttribute(AwsSignerExecutionAttribute.SIGNING_REGION, region);

        // sign it
        return signer.sign(request, attributes);
    }

    /**
     * Returns an {@link URI} from an HTTP context.
     *
     * @param context request context
     * @param uri request line URI
     * @return an {@link URI} from an HTTP context
     * @throws IOException if the {@code uri} syntax is invalid
     */
    static URI buildUri(final HttpContext context, final String uri) throws IOException {
        try {
            URIBuilder uriBuilder = new URIBuilder(uri);

            HttpHost host = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
            if (host != null) {
                uriBuilder.setHost(host.getHostName());
                uriBuilder.setScheme(host.getSchemeName());
                uriBuilder.setPort(host.getPort());
            }
            return uriBuilder.build();
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid URI", ex);
        }
    }
}
