package io.github.acm19.aws.interceptor.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpCoreContext;
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

class AwsRequestSigningApacheInterceptorTest {
    private AwsRequestSigningApacheInterceptor interceptor;

    @BeforeEach
    void createInterceptor() {
        AwsCredentialsProvider anonymousCredentialsProvider = StaticCredentialsProvider
                .create(AnonymousCredentialsProvider.create().resolveCredentials());
        interceptor = new AwsRequestSigningApacheInterceptor("servicename",
                new AddHeaderSigner("Signature", "wuzzle"),
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);
    }

    @Test
    void testGetSigner() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(new MockRequestLine("/query?a=b"));
        request.addHeader("foo", "bar");
        request.addHeader("content-length", "0");

        HttpCoreContext context = new HttpCoreContext();
        context.setTargetHost(HttpHost.create("localhost"));

        interceptor.process(request, context);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());
        assertNull(request.getFirstHeader("content-length"));
    }

    @Test
    void testPostSigner() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                new MockRequestLine("POST", "/query?a=b"));

        String payload = "{\"test\": \"val\"}";
        BasicHttpEntity httpEntity = new BasicHttpEntity();
        httpEntity.setContentType("text/html; charset=UTF-8");
        final byte[] payloadData = payload.getBytes();
        httpEntity.setContent(new ByteArrayInputStream(payloadData));
        httpEntity.setContentLength(payloadData.length);

        request.setEntity(httpEntity);

        request.addHeader("foo", "bar");

        HttpCoreContext context = new HttpCoreContext();
        context.setTargetHost(HttpHost.create("localhost"));

        interceptor.process(request, context);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());

        assertEquals(Long.toString(httpEntity.getContentLength()),
                request.getFirstHeader("signedContentLength").getValue());

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        request.getEntity().writeTo(outputStream);
        assertEquals(payload, outputStream.toString());
    }

    @Test
    void testBadRequest() throws Exception {
        HttpRequest badRequest = new BasicHttpRequest("GET", "?#!@*%");
        assertThrows(IOException.class, () -> {
            interceptor.process(badRequest, new BasicHttpContext());
        });
    }

    @Test
    void testEncodedUriSigner() throws Exception {
        String data = "I'm an entity";
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                new MockRequestLine("/foo-2017-02-25%2Cfoo-2017-02-26/_search?a=b"));
        request.setEntity(new StringEntity(data));
        request.addHeader("foo", "bar");
        request.addHeader("content-length", "0");

        HttpCoreContext context = new HttpCoreContext();
        context.setTargetHost(HttpHost.create("localhost"));

        interceptor.process(request, context);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());
        assertNull(request.getFirstHeader("content-length"));
        assertEquals("/foo-2017-02-25%2Cfoo-2017-02-26/_search", request.getFirstHeader("resourcePath").getValue());
        assertEquals(Long.toString(data.length()), request.getFirstHeader("signedContentLength").getValue());
    }

    @Test
    void testGzipCompressedContent() throws Exception {
        String data = "data";

        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                new MockRequestLine("POST", "/query?a=b"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
        gzipOutputStream.write(data.getBytes("UTF-8"));
        gzipOutputStream.close();

        ByteArrayEntity entity = new ByteArrayEntity(outputStream.toByteArray(),
                ContentType.DEFAULT_BINARY);
        entity.setContentEncoding("gzip");
        request.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        request.setEntity(entity);

        HttpCoreContext context = new HttpCoreContext();
        context.setTargetHost(HttpHost.create("localhost"));

        interceptor.process(request, context);

        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());

        assertEquals(Long.toString(entity.getContentLength()),
                request.getFirstHeader("signedContentLength").getValue());
    }

    private static class AddHeaderSigner implements Signer {
        private final String name;
        private final String value;

        private AddHeaderSigner(String name, String value) {
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

    private static class MockRequestLine implements RequestLine {
        private final String uri;
        private final String method;

        public MockRequestLine(String uri) {
            this("POST", uri);
        }

        public MockRequestLine(String method, String uri) {
            this.method = method;
            this.uri = uri;
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
