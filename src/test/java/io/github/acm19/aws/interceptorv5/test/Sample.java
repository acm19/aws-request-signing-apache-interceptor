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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

class Sample {
    private static final int SCREEN_WIDTH = 160;

    protected String endpoint;
    protected Region region;
    protected String service;

    Sample(final String service, final String[] args) throws ParseException {
        this.service = service;
        parseOptions(args);
    }

    Sample(final String service, final String endpoint, final Region region) {
        this.endpoint = endpoint;
        this.region = region;
        this.service = service;
    }

    public static void main(final String[] args) throws IOException, ParseException {
        Sample sampleClass = new Sample("", args);
        sampleClass.makeGetRequest();
        sampleClass.makePostRequest();
    }

    private void parseOptions(final String[] args) throws ParseException {
        Options options = new Options()
                .addRequiredOption(null, "endpoint", true, "OpenSearch endpoint")
                .addRequiredOption(null, "region", true, "AWS signing region");

        try {
            CommandLine cmd = new DefaultParser().parse(options, args);
            this.endpoint = cmd.getOptionValue("endpoint");
            this.region = Region.of(cmd.getOptionValue("region"));
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            new HelpFormatter().printHelp(
                    SCREEN_WIDTH,
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
                    null);
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

    void logRequest(final HttpUriRequest request) throws IOException {
        CloseableHttpClient httpClient = signingClient();
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            switch (response.getCode()) {
                case HttpStatus.SC_OK:
                case HttpStatus.SC_CREATED:
                    break;
                default:
                    throw new RuntimeException(response.getReasonPhrase());
            }
        }
    }

    CloseableHttpClient signingClient() {
        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                service,
                Aws4Signer.create(),
                DefaultCredentialsProvider.create(),
                region);

        return HttpClients.custom()
                .addRequestInterceptorLast(interceptor)
                .build();
    }
}
