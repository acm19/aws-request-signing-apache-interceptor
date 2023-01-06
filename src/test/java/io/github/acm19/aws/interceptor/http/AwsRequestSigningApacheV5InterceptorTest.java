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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.signer.Signer;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.IoUtils;

class AwsRequestSigningApacheV5InterceptorTest {
    private static final BasicEntityDetails ENTITY_DETAILS = new BasicEntityDetails(0, ContentType.TEXT_XML);
    private AwsRequestSigningApacheV5Interceptor interceptor;

    @BeforeEach
    void createInterceptor() {
        interceptor = buildInterceptor("servicename", new AddHeaderSigner("Signature", "wuzzle"));
    }

    private static AwsRequestSigningApacheV5Interceptor buildInterceptor(String serviceName, AddHeaderSigner signer) {
        AwsCredentialsProvider anonymousCredentialsProvider = StaticCredentialsProvider
                .create(AnonymousCredentialsProvider.create().resolveCredentials());
        return new AwsRequestSigningApacheV5Interceptor(serviceName, signer,
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);
    }

    @Test
    void signGetRequest() throws Exception {
        HttpRequest request = new HttpGet("http://localhost/query?a=b");
        request.addHeader("foo", "bar");
        request.addHeader("content-length", "0");

        interceptor.process(request, ENTITY_DETAILS, null);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());
        assertNull(request.getFirstHeader("content-length"));
    }

    @Test
    void signPostRequest() throws Exception {
        HttpPost request = new HttpPost("http://localhost/query?a=b");
        request.addHeader("foo", "bar");

        String payload = "{\"test\": \"val\"}";
        final byte[] payloadData = payload.getBytes();
        BasicHttpEntity httpEntity = new BasicHttpEntity(new ByteArrayInputStream(payloadData),
                payloadData.length, ContentType.TEXT_XML);
        request.setEntity(httpEntity);

        interceptor.process(request, ENTITY_DETAILS, null);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());

        assertEquals(Long.toString(httpEntity.getContentLength()),
                request.getFirstHeader("signedContentLength").getValue());

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        request.getEntity().writeTo(outputStream);
        assertEquals(payload, outputStream.toString());
    }

    @Test
    void signRepeatableEntity() throws Exception {
        HttpPost request = new HttpPost("http://localhost/");
        request.setEntity(new StringEntity("{\"test\": \"val\"}"));
        interceptor.process(request, ENTITY_DETAILS, null);

        assertTrue(request.getEntity().isRepeatable());
    }

    @Test
    void signEncodedUri() throws Exception {
        String data = "I'm an entity";
        HttpPost request = new HttpPost("http://localhost/foo-2017-02-25%2Cfoo-2017-02-26/_search?a=b");
        request.setEntity(new StringEntity(data));
        request.addHeader("foo", "bar");
        request.addHeader("content-length", "0");

        interceptor.process(request, ENTITY_DETAILS, null);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());
        assertNull(request.getFirstHeader("content-length"));
        assertEquals("/foo-2017-02-25%2Cfoo-2017-02-26/_search", request.getFirstHeader("resourcePath").getValue());
        assertEquals(Long.toString(data.length()), request.getFirstHeader("signedContentLength").getValue());
    }

    @Test
    void signGzipCompressedContent() throws Exception {
        String data = "data";
        HttpPost request = new HttpPost("http://localhost/query?a=b");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data.getBytes("UTF-8"));
        }

        ByteArrayEntity entity = new ByteArrayEntity(outputStream.toByteArray(), ContentType.DEFAULT_BINARY);
        request.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        request.setEntity(entity);

        interceptor.process(request, ENTITY_DETAILS, null);

        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());

        assertEquals(Long.toString(entity.getContentLength()),
                request.getFirstHeader("signedContentLength").getValue());
    }

    @Test
    void signOpenSearchServerlessRequest() throws HttpException, IOException {
        interceptor = buildInterceptor("aoss", new AssertNoContentLenghtSigner("Signature", "wuzzle"));
        HttpPost request = new HttpPost("http://localhost/query?a=b");
        request.addHeader("foo", "bar");
        request.addHeader("content-length", "10");

        String payload = "{\"test\": \"val\"}";
        final byte[] payloadData = payload.getBytes();
        BasicHttpEntity httpEntity = new BasicHttpEntity(new ByteArrayInputStream(payloadData),
                payloadData.length, ContentType.TEXT_XML);
        request.setEntity(httpEntity);

        interceptor.process(request, ENTITY_DETAILS, null);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());
        assertEquals("10", request.getFirstHeader("content-length").getValue());

        assertEquals(Long.toString(httpEntity.getContentLength()),
                request.getFirstHeader("signedContentLength").getValue());

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        request.getEntity().writeTo(outputStream);
        assertEquals(payload, outputStream.toString());
    }

    private static class AddHeaderSigner implements Signer {
        private final String name;
        private final String value;

        protected AddHeaderSigner(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public SdkHttpFullRequest sign(SdkHttpFullRequest request, ExecutionAttributes ea) {
            SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                    .uri(request.getUri())
                    .method(request.method())
                    .headers(request.headers())
                    .appendHeader(name, value)
                    .appendHeader("resourcePath", request.getUri().getRawPath());

            if (request.contentStreamProvider().isPresent()) {
                ContentStreamProvider contentStreamProvider = request.contentStreamProvider().get();
                requestBuilder.contentStreamProvider(contentStreamProvider);
                requestBuilder.appendHeader("signedContentLength",
                        Long.toString(getContentLength(contentStreamProvider.newStream())));
            }

            return requestBuilder.build();
        }

        private static int getContentLength(InputStream content) {
            try {
                return IoUtils.toByteArray(content).length;
            } catch (IOException e) {
                return -1;
            }
        }
    }

    private static final class AssertNoContentLenghtSigner extends AddHeaderSigner {
        private AssertNoContentLenghtSigner(String name, String value) {
            super(name, value);
        }

        @Override
        public SdkHttpFullRequest sign(SdkHttpFullRequest request, ExecutionAttributes ea) {
            assertNull(request.headers().get("content-length"));
            return super.sign(request, ea);
        }
    }
}
