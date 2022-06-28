package io.github.acm19.aws.interceptor.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.NoSuchElementException;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
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
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

class AwsRequestSigningApacheInterceptorTest {
    private AwsRequestSigningApacheInterceptor interceptor;

    @BeforeEach
    void createInterceptor() {
        AwsCredentialsProvider anonymousCredentialsProvider =
                StaticCredentialsProvider.create(AnonymousCredentialsProvider.create().resolveCredentials());
        interceptor = new AwsRequestSigningApacheInterceptor("servicename",
                new AddHeaderSigner("Signature", "wuzzle"),
                anonymousCredentialsProvider,
                Region.AF_SOUTH_1);
    }

    @Test
    void testSimpleSigner() throws Exception {
        HttpEntityEnclosingRequest request =
                new BasicHttpEntityEnclosingRequest(new MockRequestLine("/query?a=b"));
        request.setEntity(new StringEntity("I'm an entity"));
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
    void testBadRequest() throws Exception {
        HttpRequest badRequest = new BasicHttpRequest("GET", "?#!@*%");
		assertThrows(IOException.class, () -> {
				interceptor.process(badRequest, new BasicHttpContext());
		});
    }

    @Test
    void testEncodedUriSigner() throws Exception {
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                new MockRequestLine("/foo-2017-02-25%2Cfoo-2017-02-26/_search?a=b"));
        request.setEntity(new StringEntity("I'm an entity"));
        request.addHeader("foo", "bar");
        request.addHeader("content-length", "0");

        HttpCoreContext context = new HttpCoreContext();
        context.setTargetHost(HttpHost.create("localhost"));

        interceptor.process(request, context);

        assertEquals("bar", request.getFirstHeader("foo").getValue());
        assertEquals("wuzzle", request.getFirstHeader("Signature").getValue());
        assertNull(request.getFirstHeader("content-length"));
        assertEquals("/foo-2017-02-25%2Cfoo-2017-02-26/_search", request.getFirstHeader("resourcePath").getValue());
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
            return SdkHttpFullRequest.builder()
                    .uri(request.getUri())
                    .method(SdkHttpMethod.GET)
                    .contentStreamProvider(request.contentStreamProvider().orElseThrow(NoSuchElementException::new))
                    .headers(request.headers())
                    .appendHeader(name, value)
                    .appendHeader("resourcePath", request.getUri().getRawPath())
                    .build();
        }
    }

    private static class MockRequestLine implements RequestLine {
        private final String uri;

        public MockRequestLine(String uri) { this.uri = uri; }

        @Override public String getMethod() { return "GET"; }
        @Override public String getUri() { return uri; }

        @Override
        public ProtocolVersion getProtocolVersion() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
