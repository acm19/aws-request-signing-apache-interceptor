/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class AwsRequestSigningApacheV5InterceptorTest {
    @ParameterizedTest
    @MethodSource("allClients")
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TestAllClients {
    }

    static Stream<Arguments> allClients() {
        AwsCredentialsProvider anonymousCredentialsProvider = StaticCredentialsProvider
                .create(AnonymousCredentialsProvider.create().resolveCredentials());
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "servicename",
                new AddHeaderSigner("Signature", "wuzzle"),
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);
        AwsRequestSigningApacheV5ExecInterceptor execInterceptor = new AwsRequestSigningApacheV5ExecInterceptor(
                "servicename",
                new AddHeaderSigner("Signature", "wuzzle"),
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);

        return Stream.of(
                Arguments.of(Named.of("SyncRequestInterceptor", new ApacheV5SyncRequestInterceptorAdapter(interceptor))),
                Arguments.of(Named.of("AsyncRequestInterceptor", new ApacheV5AsyncRequestInterceptorAdapter(interceptor))),
                Arguments.of(Named.of("AsyncExecInterceptor", new ApacheV5AsyncExecInterceptorAdapter(execInterceptor)))
        );
    }

    private static final BasicEntityDetails ENTITY_DETAILS = new BasicEntityDetails(0, ContentType.TEXT_XML);
    private MockWebServer server;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse());
        server.start();
    }

    @AfterEach
    void cleanup() throws IOException {
        server.shutdown();
    }

    @TestAllClients
    void signGetRequest(ApacheV5ClientAdapter client) throws Exception {
        HttpGet request = new HttpGet(server.url("/query?a=b").toString());
        request.addHeader("foo", "bar");

        client.execute(request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("bar", recorded.getHeader("foo"));
        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));
        assertNull(recorded.getHeader("content-length"));
    }

    @TestAllClients
    void signPostRequest(ApacheV5ClientAdapter client) throws Exception {
        HttpPost request = new HttpPost(server.url("/query?a=b").toString());
        request.addHeader("foo", "bar");

        String payload = "{\"test\": \"val\"}";
        final byte[] payloadData = payload.getBytes();
        BasicHttpEntity httpEntity = new BasicHttpEntity(new ByteArrayInputStream(payloadData),
                payloadData.length, ContentType.TEXT_XML);
        request.setEntity(httpEntity);

        client.execute(request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("bar", recorded.getHeader("foo"));
        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

        assertEquals(Long.toString(httpEntity.getContentLength()),
                recorded.getHeader("signedContentLength"));
        assertEquals(payload, recorded.getBody().readUtf8());
    }

    @TestAllClients
    void testEncodedUriSigner(ApacheV5ClientAdapter client) throws Exception {
        String data = "I'm an entity";
        HttpPost request = new HttpPost(server.url("/foo-2017-02-25%2Cfoo-2017-02-26/_search?a=b").toString());
        request.setEntity(new StringEntity(data));
        request.addHeader("foo", "bar");

        client.execute(request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("bar", recorded.getHeader("foo"));
        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));
        assertEquals("/foo-2017-02-25%2Cfoo-2017-02-26/_search", recorded.getHeader("resourcePath"));
        assertEquals(Long.toString(data.length()), recorded.getHeader("signedContentLength"));
    }

    @TestAllClients
    void testGzipCompressedContent(ApacheV5ClientAdapter client) throws Exception {
        String data = "data";
        HttpPost request = new HttpPost(server.url("/query?a=b").toString());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data.getBytes("UTF-8"));
        }

        ByteArrayEntity entity = new ByteArrayEntity(outputStream.toByteArray(), ContentType.DEFAULT_BINARY);
        request.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        request.setEntity(entity);

        client.execute(request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

        assertEquals(Long.toString(entity.getContentLength()),
                recorded.getHeader("signedContentLength"));
    }
}
