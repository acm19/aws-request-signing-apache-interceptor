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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class AwsRequestSigningApacheInterceptorTest {
    private CloseableHttpClient client;
    private HttpHost host;
    private MockWebServer server;

    @BeforeEach
    void setup() throws IOException {
        AwsCredentialsProvider anonymousCredentialsProvider = StaticCredentialsProvider
                .create(AnonymousCredentialsProvider.create().resolveCredentials());
        AwsRequestSigningApacheInterceptor interceptor = new AwsRequestSigningApacheInterceptor("servicename",
                new AddHeaderSigner("Signature", "wuzzle"),
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);
        client = HttpClients.custom()
                .addInterceptorLast(interceptor)
                .build();

        server = new MockWebServer();
        server.enqueue(new MockResponse());
        server.start();
        host = new HttpHost(server.getHostName(), server.getPort());
    }

    @AfterEach
    void cleanup() throws IOException {
        server.shutdown();
        client.close();
    }

    @Test
    void signGetRequest() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
            "GET", server.url("/query?a=b").toString());
        request.addHeader("foo", "bar");

        client.execute(host, request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("bar", recorded.getHeader("foo"));
        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertNull(recorded.getHeader("content-length"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));
    }

    @Test
    void signPostRequest() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", server.url("/query?a=b").toString());

        String payload = "{\"test\": \"val\"}";
        BasicHttpEntity httpEntity = new BasicHttpEntity();
        httpEntity.setContentType("text/html; charset=UTF-8");
        final byte[] payloadData = payload.getBytes();
        httpEntity.setContent(new ByteArrayInputStream(payloadData));
        httpEntity.setContentLength(payloadData.length);

        request.setEntity(httpEntity);

        request.addHeader("foo", "bar");

        client.execute(host, request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("bar", recorded.getHeader("foo"));
        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

        assertEquals(Long.toString(httpEntity.getContentLength()),
                recorded.getHeader("signedContentLength"));

        assertEquals(payload, recorded.getBody().readUtf8());
    }


    @Test
    void testBadRequest() throws Exception {
        HttpRequest badRequest = new BasicHttpRequest("GET", "?#!@*%");
        assertThrows(IOException.class, () -> {
            client.execute(host, badRequest);
        });
    }

    @Test
    void testEncodedUriSigner() throws Exception {
        String data = "I'm an entity";
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", server.url("/foo-2017-02-25%2Cfoo-2017-02-26/_search?a=b").toString());
        request.setEntity(new StringEntity(data));
        request.addHeader("foo", "bar");

        client.execute(host, request);
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

        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", server.url("/query?a=b").toString());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data.getBytes("UTF-8"));
        }

        ByteArrayEntity entity = new ByteArrayEntity(outputStream.toByteArray(), ContentType.DEFAULT_BINARY);
        entity.setContentEncoding("gzip");
        request.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        request.setEntity(entity);

        client.execute(host, request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

        assertEquals(Long.toString(entity.getContentLength()),
                    recorded.getHeader("signedContentLength"));
    }
}
