/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.IoUtils;

class Sample {
    private static final int SCREEN_WIDTH = 160;

    protected String endpoint;
    protected Region region;
    protected String service;

    Sample(final String[] args) throws ParseException {
        parseOptions(args);
    }

    Sample(final String service, final String endpoint, final Region region) {
        this.endpoint = endpoint;
        this.region = region;
        this.service = service;
    }

    public static void main(final String[] args) throws IOException, ParseException {
        Sample sampleClass = new Sample(args);
        sampleClass.makeGetRequest();
        sampleClass.makePostRequest();
    }

    private void parseOptions(final String[] args) throws ParseException {
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
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
        try (CloseableHttpClient httpClient = signingClient()) {
            httpClient.execute(request, response -> {
                System.out.println(response.getStatusLine());
                System.out.println(IoUtils.toUtf8String(response.getEntity().getContent()));
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                    case HttpStatus.SC_CREATED:
                        return true;
                    default:
                        throw new RuntimeException(response.getStatusLine().getReasonPhrase());
                }
            });
        }
    }

    CloseableHttpClient signingClient() {
        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(
                service,
                AwsV4HttpSigner.create(),
                DefaultCredentialsProvider.create(),
                region);

        return HttpClients.custom()
                .addInterceptorLast(interceptor)
                .build();
    }
}
