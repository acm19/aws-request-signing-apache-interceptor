/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.signer.Signer;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

/**
 * An {@link HttpRequestInterceptor} that signs requests for any AWS service
 * running in a specific region using an AWS {@link Signer} and
 * {@link AwsCredentialsProvider}.
 */
public final class AwsRequestSigningApacheInterceptor implements HttpRequestInterceptor {
    private final RequestSigner signer;

    /**
     * Creates an {@code AwsRequestSigningApacheInterceptor} with the
     * ability to sign request for a specific service in a region and
     * defined credentials.
     *
     * @param service                service the client is connecting to
     * @param signer                 signer implementation.
     * @param awsCredentialsProvider source of AWS credentials for signing
     * @param region                 signing region
     */
    public AwsRequestSigningApacheInterceptor(
            final String service,
            final Signer signer,
            final AwsCredentialsProvider awsCredentialsProvider,
            final Region region) {
        this.signer = new RequestSigner(service, signer, awsCredentialsProvider, region);
    }

    /**
     * Creates an {@code AwsRequestSigningApacheInterceptor} with the
     * ability to sign request for a specific service in a region and
     * defined credentials.
     *
     * @param service                service the client is connecting to
     * @param signer                 signer implementation
     * @param awsCredentialsProvider source of AWS credentials for signing
     * @param region                 signing region
     */
    public AwsRequestSigningApacheInterceptor(
            final String service,
            final Signer signer,
            final AwsCredentialsProvider awsCredentialsProvider,
            final String region) {
        this(service, signer, awsCredentialsProvider, Region.of(region));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpRequest request, final HttpContext context)
            throws HttpException, IOException {
        URI requestUri = RequestSigner.buildUri(context, request.getRequestLine().getUri());

        // copy Apache HttpRequest to AWS request
        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.fromValue(request.getRequestLine().getMethod()))
                .uri(requestUri);

        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest httpEntityEnclosingRequest = (HttpEntityEnclosingRequest) request;
            if (httpEntityEnclosingRequest.getEntity() != null) {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                httpEntityEnclosingRequest.getEntity().writeTo(outputStream);
                requestBuilder.contentStreamProvider(() -> new ByteArrayInputStream(outputStream.toByteArray()));
            }
        }

        Map<String, List<String>> headers = headerArrayToMap(request.getAllHeaders());
        // adds a hash of the request payload when signing
        headers.put("x-amz-content-sha256", Collections.singletonList("required"));
        requestBuilder.headers(headers);
        SdkHttpFullRequest signedRequest = signer.signRequest(requestBuilder.build());

        // copy everything back
        request.setHeaders(mapToHeaderArray(signedRequest.headers()));

        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest httpEntityEnclosingRequest = (HttpEntityEnclosingRequest) request;
            if (httpEntityEnclosingRequest.getEntity() != null) {
                BasicHttpEntity basicHttpEntity = new BasicHttpEntity();
                basicHttpEntity.setContent(signedRequest.contentStreamProvider()
                        .orElseThrow(() -> new IllegalStateException("There must be content"))
                        .newStream());
                // wrap into repeatable entity to support retries
                httpEntityEnclosingRequest.setEntity(new BufferedHttpEntity(basicHttpEntity));
            }
        }
    }

    private static Map<String, List<String>> headerArrayToMap(final Header[] headers) {
        Map<String, List<String>> headersMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Header header : headers) {
            if (!skipHeader(header)) {
                headersMap.put(header.getName(),
                               headersMap.getOrDefault(header.getName(),
                                                       new LinkedList<>(Collections.singletonList(header.getValue()))));
            }
        }
        return headersMap;
    }

    private static boolean skipHeader(final Header header) {
        return (HTTP.CONTENT_LEN.equalsIgnoreCase(header.getName())
                && "0".equals(header.getValue())) // Strip Content-Length: 0
                || HTTP.TARGET_HOST.equalsIgnoreCase(header.getName()); // Host comes from endpoint
    }

    private static Header[] mapToHeaderArray(final Map<String, List<String>> mapHeaders) {
        Header[] headers = new Header[mapHeaders.size()];
        int i = 0;
        for (Map.Entry<String, List<String>> headerEntry : mapHeaders.entrySet()) {
            for (String value : headerEntry.getValue()) {
                headers[i++] = new BasicHeader(headerEntry.getKey(), value);
            }
        }
        return headers;
    }
}
