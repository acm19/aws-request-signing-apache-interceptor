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
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
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
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.auth.aws.internal.signer.DefaultAwsV4HttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignRequest;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignedRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.IoUtils;

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

    private static final class AddHeaderSigner implements AwsV4HttpSigner {
        AwsV4HttpSigner signer = new DefaultAwsV4HttpSigner();

        private final String name;
        private final String value;

        private AddHeaderSigner(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public SignedRequest sign(SignRequest signRequest) {
            SdkHttpFullRequest.Builder request = SdkHttpFullRequest.builder()
                    .uri(signRequest.request().getUri())
                    .method(signRequest.request().method())
                    .headers(signRequest.request().headers())
                    .appendHeader(name, value)
                    .appendHeader("resourcePath", signRequest.request().getUri().getRawPath());

            if (signRequest.payload().isPresent()) {
                ContentStreamProvider contentStreamProvider = (ContentStreamProvider) signRequest.payload().get();
                InputStream payloadStream = contentStreamProvider.newStream();
                ContentStreamProvider newContentStreamProvider = ContentStreamProvider.fromInputStream(payloadStream);

                request.contentStreamProvider(newContentStreamProvider)
                        .appendHeader("signedContentLength",
                                Long.toString(getContentLength(newContentStreamProvider.newStream())))
                        .build();
            }

            SdkHttpFullRequest requestBuilt = request.build();

            SignedRequest signedRequest =
                signer.sign(r -> r
                        .identity(AnonymousCredentialsProvider.create().resolveCredentials())
                        .request(requestBuilt)
                        .payload(requestBuilt.contentStreamProvider().orElse(null))
                        .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "servicename")
                        .putProperty(AwsV4HttpSigner.REGION_NAME, "us-west-2")
                        .putProperty(AwsV4HttpSigner.DOUBLE_URL_ENCODE, false) // Required for S3 only
                        .putProperty(AwsV4HttpSigner.NORMALIZE_PATH, false)); // Required for S3 only

            return signedRequest;
        }

        private static int getContentLength(final InputStream content) {
            try {
                return IoUtils.toByteArray(content).length;
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public CompletableFuture<AsyncSignedRequest> signAsync(AsyncSignRequest asyncSignRequest) {
            return null;
        }
    }
}
