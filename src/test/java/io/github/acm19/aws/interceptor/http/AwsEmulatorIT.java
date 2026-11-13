/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import static org.apache.hc.core5.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.regions.Region;

class AwsEmulatorIT {
    private static final String PAYLOAD = "{\"Limit\":10}";
    private static final String TARGET = "DynamoDB_20120810.ListTables";
    private static final String JSON_CONTENT_TYPE = "application/x-amz-json-1.0";
    private static final int REQUEST_TIMEOUT_SECONDS = 10;
    private static String endpoint;
    private static AwsBasicCredentials credentials;
    private static SSLContext sslContext;

    private enum Client {
        APACHE_4, APACHE_5_SYNC, APACHE_5_ASYNC
    }

    @BeforeAll
    static void setup() throws Exception {
        endpoint = Objects.requireNonNull(System.getProperty("aws.emulator.endpoint"),
                "Run integration tests with bash integration-tests/run.sh");
        URI uri = URI.create(endpoint);
        assertEquals("https", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost(), "Integration tests must only contact the local emulator");
        Path directory = Paths.get(Objects.requireNonNull(System.getProperty("aws.emulator.directory")));
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(directory.resolve("emulator_credentials"))) {
            properties.load(reader);
        }
        credentials = AwsBasicCredentials.create(properties.getProperty("aws_access_key_id"),
                properties.getProperty("aws_secret_access_key"));

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (InputStream certificate = Files.newInputStream(directory.resolve("ca.pem"))) {
            trustStore.setCertificateEntry("emulator",
                    CertificateFactory.getInstance("X.509").generateCertificate(certificate));
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trustStore);
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, factory.getTrustManagers(), null);
    }

    @TestFactory
    Stream<DynamicTest> acceptsSignedGetWithEncodedQuery() {
        return Stream.of(Client.values()).map(client -> dynamicTest(client.name(), () -> {
            Response response = send(client, StaticCredentialsProvider.create(credentials), null,
                    "/?prefix=signing%2Btests&max-buckets=10");
            assertEquals(SC_OK, response.status, response.body);
            assertTrue(response.body.contains("<ListAllMyBucketsResult"), response.body);
        }));
    }

    @TestFactory
    Stream<DynamicTest> acceptsSignedPostWithPayload() {
        return Stream.of(Client.values()).map(client -> dynamicTest(client.name(), () -> {
            Response response = send(client, StaticCredentialsProvider.create(credentials), PAYLOAD, "/");
            assertEquals(SC_OK, response.status, response.body);
            assertTrue(response.body.contains("\"TableNames\""), response.body);
        }));
    }

    @TestFactory
    Stream<DynamicTest> rejectsIncorrectSecretForValidAccessKey() {
        return Stream.of(Client.values()).map(client -> dynamicTest(client.name(), () -> {
            AwsCredentialsProvider wrongSecret = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(credentials.accessKeyId(), "incorrect-emulator-secret"));
            Response response = send(client, wrongSecret, PAYLOAD, "/");
            assertEquals(SC_FORBIDDEN, response.status, response.body);
            assertTrue(response.body.contains("InvalidSignatureException"), response.body);
        }));
    }

    @TestFactory
    Stream<DynamicTest> rejectsUnsignedRequests() {
        return Stream.of(Client.values()).map(client -> dynamicTest(client.name(), () -> {
            Response response = send(client, null, PAYLOAD, "/");
            assertEquals(SC_FORBIDDEN, response.status, response.body);
        }));
    }

    private static Response send(Client client, AwsCredentialsProvider provider, String body, String path)
            throws Exception {
        String service = body == null ? "s3" : "dynamodb";
        switch (client) {
            case APACHE_4:
                return sendApache4(service, provider, body, path);
            case APACHE_5_SYNC:
                return sendApache5(service, provider, body, path);
            case APACHE_5_ASYNC:
                return sendApache5Async(service, provider, body, path);
            default:
                throw new IllegalArgumentException("Unsupported client: " + client);
        }
    }

    private static Response sendApache4(String service, AwsCredentialsProvider provider, String body, String path)
            throws Exception {
        org.apache.http.impl.client.HttpClientBuilder builder = org.apache.http.impl.client.HttpClients.custom()
                .setSSLContext(sslContext)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .setDefaultRequestConfig(org.apache.http.client.config.RequestConfig.custom()
                        .setConnectTimeout(Math.toIntExact(TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)))
                        .setSocketTimeout(Math.toIntExact(TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS))).build());
        if (provider != null) {
            builder.addInterceptorLast(new AwsRequestSigningApacheInterceptor(
                    service, AwsV4HttpSigner.create(), provider, Region.US_EAST_1));
        }
        HttpUriRequest request;
        if (body == null) {
            request = new HttpGet(endpoint + path);
        } else {
            HttpPost post = new HttpPost(endpoint + path);
            post.setEntity(new org.apache.http.entity.StringEntity(body,
                    org.apache.http.entity.ContentType.create(JSON_CONTENT_TYPE)));
            post.setHeader("X-Amz-Target", TARGET);
            request = post;
        }
        try (org.apache.http.impl.client.CloseableHttpClient client = builder.build();
                CloseableHttpResponse response = client.execute(request)) {
            return new Response(response.getStatusLine().getStatusCode(),
                    org.apache.http.util.EntityUtils.toString(response.getEntity()));
        }
    }

    private static Response sendApache5(String service, AwsCredentialsProvider provider, String body, String path)
            throws Exception {
        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build())
                        .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                                .setSslContext(sslContext).buildClassic()).build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build())
                .disableAutomaticRetries()
                .disableRedirectHandling();
        if (provider != null) {
            builder.addExecInterceptorLast("aws-signing", new AwsRequestSigningApacheV5Interceptor(
                    service, AwsV4HttpSigner.create(), provider, Region.US_EAST_1));
        }
        HttpUriRequestBase request = new HttpUriRequestBase(body == null ? "GET" : "POST", URI.create(endpoint + path));
        if (body != null) {
            request.setEntity(new StringEntity(body, ContentType.create(JSON_CONTENT_TYPE)));
            request.setHeader("X-Amz-Target", TARGET);
        }
        try (CloseableHttpClient client = builder.build()) {
            return client.execute(request, response ->
                    new Response(response.getCode(), EntityUtils.toString(response.getEntity())));
        }
    }

    private static Response sendApache5Async(String service, AwsCredentialsProvider provider, String body, String path)
            throws Exception {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build())
                        .setTlsStrategy(ClientTlsStrategyBuilder.create()
                                .setSslContext(sslContext).buildAsync()).build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build())
                .disableAutomaticRetries()
                .disableRedirectHandling();
        if (provider != null) {
            builder.addExecInterceptorLast("aws-signing", new AwsRequestSigningApacheV5Interceptor(
                    service, AwsV4HttpSigner.create(), provider, Region.US_EAST_1));
        }
        SimpleHttpRequest request = new SimpleHttpRequest(body == null ? "GET" : "POST", URI.create(endpoint + path));
        if (body != null) {
            request.setBody(body, ContentType.create(JSON_CONTENT_TYPE));
            request.setHeader("X-Amz-Target", TARGET);
        }
        try (CloseableHttpAsyncClient client = builder.build()) {
            client.start();
            SimpleHttpResponse response = client.execute(request, null)
                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new Response(response.getCode(), response.getBodyText());
        }
    }

    private static final class Response {
        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
