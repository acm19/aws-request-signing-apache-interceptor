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
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
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
public final class AwsRequestSigningApacheV5Interceptor implements HttpRequestInterceptor {
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
    public AwsRequestSigningApacheV5Interceptor(String service,
                                                Signer signer,
                                                AwsCredentialsProvider awsCredentialsProvider,
                                                Region region) {
        this.signer = new RequestSigner(service, signer, awsCredentialsProvider, region);
        this.isServerlessOpenSearch = "aoss".equals(service);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(HttpRequest request, EntityDetails entityDetails, HttpContext context)
            throws HttpException, IOException {
        // copy Apache HttpRequest to AWS request
        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.fromValue(request.getMethod()))
                .uri(buildUri(request));

        if (request instanceof ClassicHttpRequest) {
            ClassicHttpRequest classicHttpRequest = (ClassicHttpRequest) request;
            if (classicHttpRequest.getEntity() != null) {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                classicHttpRequest.getEntity().writeTo(outputStream);
                requestBuilder.contentStreamProvider(() -> new ByteArrayInputStream(outputStream.toByteArray()));
            }
        }

        requestBuilder.headers(headerArrayToMap(request.getHeaders()));
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

        if (request instanceof ClassicHttpRequest) {
            ClassicHttpRequest httpEntityEnclosingRequest = (ClassicHttpRequest) request;
            if (httpEntityEnclosingRequest.getEntity() != null) {
                BasicHttpEntity basicHttpEntity = new BasicHttpEntity(signedRequest.contentStreamProvider()
                        .orElseThrow(() -> new IllegalStateException("There must be content"))
                        .newStream(), ContentType.parse(entityDetails.getContentType()));
                // wrap into repeatable entity to support retries
                httpEntityEnclosingRequest.setEntity(new BufferedHttpEntity(basicHttpEntity));
            }
        }
    }

    private static URI buildUri(HttpRequest request) throws IOException {
        try {
            return request.getUri();
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid URI", ex);
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
        return (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(header.getName())
                && (("0".equals(header.getValue())) || isServerlessOpenSearch))
                || HttpHeaders.HOST.equalsIgnoreCase(header.getName()); // Host comes from endpoint
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
