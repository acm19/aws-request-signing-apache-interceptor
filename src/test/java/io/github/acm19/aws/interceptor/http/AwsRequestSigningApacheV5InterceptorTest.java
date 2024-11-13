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
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
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

class AwsRequestSigningApacheV5InterceptorTest {
    private static final BasicEntityDetails ENTITY_DETAILS = new BasicEntityDetails(0, ContentType.TEXT_XML);
    private MockWebServer server;
    private CloseableHttpClient client;

    @BeforeEach
    void setup() throws IOException {
        AwsCredentialsProvider anonymousCredentialsProvider = StaticCredentialsProvider
                .create(AnonymousCredentialsProvider.create().resolveCredentials());
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor("servicename",
                new AddHeaderSigner("Signature", "wuzzle"),
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);
        client = HttpClients.custom()
                            .addRequestInterceptorLast(interceptor)
                            .build();

        server = new MockWebServer();
        server.enqueue(new MockResponse());
        server.start();
    }

    @AfterEach
    void cleanup() throws IOException {
        server.shutdown();
        client.close();
    }

    @Test
    void signGetRequest() throws Exception {
        HttpGet request = new HttpGet(server.url("/query?a=b").toString());
        request.addHeader("foo", "bar");

        client.execute(request);
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

    @Test
    void testEncodedUriSigner() throws Exception {
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

        client.execute(request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("wuzzle", recorded.getHeader("Signature"));
        assertEquals("required", recorded.getHeader("x-amz-content-sha256"));

        assertEquals(Long.toString(entity.getContentLength()),
                recorded.getHeader("signedContentLength"));
    }

    private static final class AddHeaderSigner implements AwsV4HttpSigner {
        private final AwsV4HttpSigner signer = new DefaultAwsV4HttpSigner();

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

                request
                        .contentStreamProvider(newContentStreamProvider)
                        .appendHeader("signedContentLength",
                                Long.toString(getContentLength(newContentStreamProvider.newStream())))
                        .build();
            }

            SdkHttpFullRequest requestBuilt = request.build();

            SignedRequest signedRequest =
                signer.sign(r -> r
                        .identity(
                                AnonymousCredentialsProvider.create().resolveCredentials())
                        .request(requestBuilt)
                        .payload(requestBuilt.contentStreamProvider().orElse(null))
                        .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "servicename")
                        .putProperty(AwsV4HttpSigner.REGION_NAME, "us-west-2")
                        .putProperty(AwsV4HttpSigner.DOUBLE_URL_ENCODE, false)
                        .putProperty(AwsV4HttpSigner.NORMALIZE_PATH, false));

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
