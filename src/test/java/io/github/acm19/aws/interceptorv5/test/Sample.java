/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptorv5.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.regions.Region;

class Sample {
    private static final int WAIT_FOR_RESPONSE = 5;

    protected String endpoint;
    protected Region region;
    protected String service;

    Sample(String[] args) throws ParseException {
        parseOptions(args);
    }

    Sample(String service, String endpoint, Region region) {
        this.endpoint = endpoint;
        this.region = region;
        this.service = service;
    }

    public static void main(String[] args) throws IOException, ParseException {
        Sample sampleClass = new Sample(args);
        sampleClass.makeGetRequest();
        sampleClass.makePostRequest();
        sampleClass.makeAsyncRequest();
    }

    private void parseOptions(String[] args) throws ParseException {
        Options options = new Options()
                .addRequiredOption(null, "endpoint", true, "OpenSearch endpoint")
                .addRequiredOption(null, "region", true, "AWS signing region")
                .addOption(null, "service", true, "AWS signing service, default is 'es'");

        try {
            CommandLine cmd = new DefaultParser().parse(options, args);
            this.endpoint = cmd.getOptionValue("endpoint");
            this.region = Region.of(cmd.getOptionValue("region"));
            this.service = cmd.getOptionValue("service", "es");
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            try {
                HelpFormatter.builder().get().printHelp(
                        String.join(" ",
                            "mvn",
                            "test-compile",
                            "exec:java",
                            "-Dexec.classpathScope=test",
                            "-Dexec.mainClass=\"" + getClass().getCanonicalName() + "\"",
                            "-Dexec.args=\"...\""
                        ),
                        null,
                        options,
                        null,
                        false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            throw e;
        }
    }

    private void makeGetRequest() throws IOException {
        HttpGet httpGet = new HttpGet(endpoint + "/homepage");
        logRequest(httpGet);
    }

    private void makePostRequest() throws IOException {
        HttpPost httpPost = new HttpPost(endpoint + "/login");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("username", "vip"));
        nvps.add(new BasicNameValuePair("password", "secret"));
        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        logRequest(httpPost);
    }

    private void makeAsyncRequest() throws IOException {
        String payload = "{\"aync\": \"request\"}";
        SimpleHttpRequest request = SimpleRequestBuilder.post(endpoint + "/login")
                .setBody(payload, ContentType.APPLICATION_JSON)
                .build();

        logAsyncRequest(request);
    }

    void logRequest(HttpUriRequest request) throws IOException {
        try (CloseableHttpClient httpClient = signingClient()) {
            httpClient.execute(request, response -> {
                switch (response.getCode()) {
                    case HttpStatus.SC_OK:
                    case HttpStatus.SC_CREATED:
                        return true;
                    default:
                        throw new RuntimeException(response.getReasonPhrase());
                }
            });
        }
    }

    private CloseableHttpClient signingClient() {
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                service,
                AwsV4HttpSigner.create(),
                DefaultCredentialsProvider.builder().build(),
                region);

        return HttpClients.custom()
                .addExecInterceptorLast("aws-signing-interceptor", interceptor)
                .build();
    }

    void logAsyncRequest(SimpleHttpRequest request) throws IOException {
        try (CloseableHttpAsyncClient httpClient = signingAsyncClient()) {
            httpClient.start();
            Future<SimpleHttpResponse> response = httpClient.execute(request, new FutureCallback<SimpleHttpResponse>() {
                @Override
                public void completed(SimpleHttpResponse response) {
                    switch (response.getCode()) {
                        case HttpStatus.SC_OK:
                        case HttpStatus.SC_CREATED:
                            break;
                        default:
                            throw new RuntimeException(response.getReasonPhrase());
                    }
                }

                @Override
                public void failed(Exception ex) {
                    throw new RuntimeException(ex);
                }

                @Override
                public void cancelled() {
                    throw new RuntimeException("Cancelled request.");
                }
            });

            response.get(WAIT_FOR_RESPONSE, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new RuntimeException(ex);
        }
    }

    private CloseableHttpAsyncClient signingAsyncClient() {
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                service,
                AwsV4HttpSigner.create(),
                DefaultCredentialsProvider.builder().build(),
                region);

        return HttpAsyncClients.custom()
                .addExecInterceptorLast("aws-signing-interceptor", interceptor)
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                        .setDefaultTlsConfig(TlsConfig.custom()
                                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                                .build())
                        .build())
                .build();
    }
}
