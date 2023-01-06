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
    private final boolean isServerlessOpenSearch;

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
    public AwsRequestSigningApacheInterceptor(String service,
                                              Signer signer,
                                              AwsCredentialsProvider awsCredentialsProvider,
                                              Region region) {
        this.signer = new RequestSigner(service, signer, awsCredentialsProvider, region);
        this.isServerlessOpenSearch = "aoss".equals(service);
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
    public AwsRequestSigningApacheInterceptor(String service,
                                              Signer signer,
                                              AwsCredentialsProvider awsCredentialsProvider,
                                              String region) {
        this(service, signer, awsCredentialsProvider, Region.of(region));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(HttpRequest request, HttpContext context)
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
        requestBuilder.headers(headerArrayToMap(request.getAllHeaders()));
        SdkHttpFullRequest signedRequest = signer.signRequest(requestBuilder.build());

        if (!isServerlessOpenSearch) {
            // copy everything back
            request.setHeaders(mapToHeaderArray(signedRequest.headers()));
        } else {
            // copy everything back, don't override headers as not all of them were used for signing the request
            for (Header header : mapToHeaderArray(signedRequest.headers())) {
                request.setHeader(header);
            }
        }

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

    private Map<String, List<String>> headerArrayToMap(Header[] headers) {
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

    /**
     * Ignores {@code Host} headers and {@code Content-Length} as long as it either
     * contains the value {@code 0} or the service to be signed is Serverless
     * OpenSearch. Details in AWS documentation below.
     *
     * AWS documentation:
     * <pre><a href="https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-clients.html
     * #serverless-signing">
     *   Signing requests to OpenSearch Serverless
     * </a></pre>
     *
     * @param header the header to evaluate
     * @return {@code true} if header must be ignored {@code false} otherwise
     */
    private boolean skipHeader(Header header) {
               // Strip for Content-Length: 0 and Serverless
        return (HTTP.CONTENT_LEN.equalsIgnoreCase(header.getName())
                && (("0".equals(header.getValue())) || isServerlessOpenSearch))
                || HTTP.TARGET_HOST.equalsIgnoreCase(header.getName()); // Host comes from endpoint
    }

    private static Header[] mapToHeaderArray(Map<String, List<String>> mapHeaders) {
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
