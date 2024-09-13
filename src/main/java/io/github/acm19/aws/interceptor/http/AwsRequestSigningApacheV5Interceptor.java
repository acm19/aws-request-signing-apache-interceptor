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
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;

/**
 * An {@link HttpRequestInterceptor} that signs requests for any AWS service
 * running in a specific region using an AWS {@link HttpSigner} and
 * {@link AwsCredentialsProvider}.
 */
public final class AwsRequestSigningApacheV5Interceptor implements HttpRequestInterceptor {
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
    public AwsRequestSigningApacheV5Interceptor(String service,
                                                HttpSigner<AwsCredentialsIdentity> signer,
                                                AwsCredentialsProvider awsCredentialsProvider,
                                                Region region) {
        this.signer = new RequestSigner(service, signer, awsCredentialsProvider, region);
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
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                classicHttpRequest.getEntity().writeTo(outputStream);
                if (!classicHttpRequest.getEntity().isRepeatable()) {
                    // copy back the entity, so it can be read again
                    BasicHttpEntity entity = new BasicHttpEntity(
                            new ByteArrayInputStream(outputStream.toByteArray()),
                            ContentType.parse(entityDetails.getContentType()));
                    // wrap into repeatable entity to support retries
                    classicHttpRequest.setEntity(new BufferedHttpEntity(entity));
                }
                requestBuilder.contentStreamProvider(() -> new ByteArrayInputStream(outputStream.toByteArray()));
            }
        }

        Map<String, List<String>> headers = headerArrayToMap(request.getHeaders());
        // adds a hash of the request payload when signing
        headers.put("x-amz-content-sha256", Collections.singletonList("required"));
        requestBuilder.headers(headers);
        SignedRequest signedRequest = signer.signRequest(requestBuilder.build());

        // copy everything back
        request.setHeaders(mapToHeaderArray(signedRequest.request().headers()));
    }

    private static URI buildUri(HttpRequest request) throws IOException {
        try {
            return request.getUri();
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid URI", ex);
        }
    }

    private static Map<String, List<String>> headerArrayToMap(Header[] headers) {
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

    private static boolean skipHeader(Header header) {
        return (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(header.getName())
                && "0".equals(header.getValue())) // Strip Content-Length: 0
                || HttpHeaders.HOST.equalsIgnoreCase(header.getName()); // Host comes from endpoint
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
