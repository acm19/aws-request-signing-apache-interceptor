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
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class AwsRequestSigningApacheV5InterceptorTest {
    private MockWebServer server;
    private AwsRequestSigningApacheV5Interceptor interceptor;

    @BeforeEach
    void setup() throws IOException {
        AwsCredentialsProvider anonymousCredentialsProvider = StaticCredentialsProvider
                .create(AnonymousCredentialsProvider.create().resolveCredentials());
        interceptor = new AwsRequestSigningApacheV5Interceptor(
                "servicename",
                new AddHeaderSigner("Signature", "wuzzle"),
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);

        server = new MockWebServer();
        server.enqueue(new MockResponse());
        server.start();
    }

    @AfterEach
    void cleanup() throws IOException {
        server.shutdown();
    }

    @Nested
    class SyncHttpClient {
        private CloseableHttpClient syncClient;

        @BeforeEach
        void setup() {
            syncClient = HttpClients.custom()
                    .addExecInterceptorLast("aws-signing-interceptor", interceptor)
                    .build();
        }

        @AfterEach
        void cleanup() throws IOException {
            syncClient.close();
        }

        @Test
        void signGetRequest() throws Exception {
            HttpGet request = new HttpGet(server.url("/query?a=b").toString());
            request.addHeader("foo", "bar");

            syncClient.execute(request, response -> "ignored");
            RecordedRequest recorded = server.takeRequest();

            assertEquals("bar", recorded.getHeader("foo"));
            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));
            assertNull(recorded.getHeader("content-length"));
        }

        @Test
        void signPostRequest() throws Exception {
            HttpPost request = new HttpPost(server.url("/query?a=b").toString());
            request.addHeader("foo", "bar");

            String payload = "{\"test\": \"val\"}";
            final byte[] payloadData = payload.getBytes();
            BasicHttpEntity httpEntity = new BasicHttpEntity(
                    new ByteArrayInputStream(payloadData),
                    payloadData.length,
                    ContentType.APPLICATION_JSON);
            request.setEntity(httpEntity);

            syncClient.execute(request, response -> "ignored");
            RecordedRequest recorded = server.takeRequest();

            assertEquals("bar", recorded.getHeader("foo"));
            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

            assertEquals(
                    Long.toString(httpEntity.getContentLength()),
                    recorded.getHeader("signedContentLength"));
            assertEquals(payload, recorded.getBody().readUtf8());
        }

        @Test
        void testEncodedUriSigner() throws Exception {
            String data = "I'm an entity";
            HttpPost request = new HttpPost(server.url("/foo-2017-02-25%2Cfoo-2017-02-26/_search?a=b").toString());
            request.setEntity(new StringEntity(data));
            request.addHeader("foo", "bar");

            syncClient.execute(request, response -> "ignored");
            RecordedRequest recorded = server.takeRequest();

            assertEquals("bar", recorded.getHeader("foo"));
            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));
            assertEquals("/foo-2017-02-25%2Cfoo-2017-02-26/_search", recorded.getHeader("resourcePath"));
            assertEquals(Long.toString(data.length()), recorded.getHeader("signedContentLength"));
        }

        @Test
        void testGzipCompressedContent() throws Exception {
            String data = "data";
            HttpPost request = new HttpPost(server.url("/query?a=b").toString());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                gzipOutputStream.write(data.getBytes("UTF-8"));
            }

            ByteArrayEntity entity = new ByteArrayEntity(outputStream.toByteArray(), ContentType.DEFAULT_BINARY);
            request.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
            request.setEntity(entity);

            syncClient.execute(request, response -> "ignored");
            RecordedRequest recorded = server.takeRequest();

            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

            assertEquals(
                    Long.toString(entity.getContentLength()),
                    recorded.getHeader("signedContentLength"));
        }
    }

    @Nested
    class AsyncHttpClient {
        private static final int TEST_SERVER_TIMEOUT_SECONDS = 5;
        private CloseableHttpAsyncClient asyncClient;

        @BeforeEach
        void setup() {
            asyncClient = HttpAsyncClients.custom()
                    .addExecInterceptorLast("aws-signing-interceptor", interceptor)
                    .build();
        }

        @AfterEach
        void cleanup() throws IOException {
            asyncClient.close();
        }

        @Test
        void signSimpleHttpGetRequest() throws Exception {
            SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/query?a=b").toString())
                    .addHeader("foo", "bar")
                    .build();

            asyncClient.start();
            asyncClient.execute(request, null);
            RecordedRequest recorded = server.takeRequest();

            assertEquals("bar", recorded.getHeader("foo"));
            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));
            assertNull(recorded.getHeader("content-length"));
        }

        @Test
        void signSimpleHttpPostRequest() throws Exception {
            String payload = "{\"test\": \"val\"}";
            SimpleHttpRequest request = SimpleRequestBuilder.post(server.url("/query?a=b").toString())
                    .addHeader("foo", "bar")
                    .setBody(payload, ContentType.APPLICATION_JSON)
                    .build();

            asyncClient.start();
            asyncClient.execute(request, null);
            RecordedRequest recorded = server.takeRequest(TEST_SERVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals("bar", recorded.getHeader("foo"));
            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

            assertEquals(
                    Long.toString(request.getBodyBytes().length),
                    recorded.getHeader("signedContentLength"));
            assertEquals(payload, recorded.getBody().readUtf8());
        }

        @Test
        void signClassicHttpGetRequest() throws Exception {
            HttpGet request = new HttpGet(server.url("/query?a=b").toString());
            request.addHeader("foo", "bar");

            asyncClient.start();
            asyncClient.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()),
                null);
            RecordedRequest recorded = server.takeRequest(TEST_SERVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals("bar", recorded.getHeader("foo"));
            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));
            assertNull(recorded.getHeader("content-length"));
        }

        @Test
        void signClassicHttpPostRequest() throws Exception {
            HttpPost request = new HttpPost(server.url("/query?a=b").toString());
            request.addHeader("foo", "bar");

            String payload = "{\"test\": \"val\"}";
            final byte[] payloadData = payload.getBytes();
            BasicHttpEntity httpEntity = new BasicHttpEntity(
                    new ByteArrayInputStream(payloadData),
                    payloadData.length,
                    ContentType.APPLICATION_JSON);
            request.setEntity(httpEntity);

            asyncClient.start();
            asyncClient.execute(
                    new BasicRequestProducer(
                            request,
                            new BasicAsyncEntityProducer(payload, ContentType.APPLICATION_JSON)),
                    new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()),
                    null);
            RecordedRequest recorded = server.takeRequest(TEST_SERVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals("bar", recorded.getHeader("foo"));
            assertEquals("wuzzle", recorded.getHeader("Signature"));
            assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

            assertEquals(
                    Long.toString(httpEntity.getContentLength()),
                    recorded.getHeader("signedContentLength"));
            assertEquals(payload, recorded.getBody().readUtf8());
        }
    }
}
