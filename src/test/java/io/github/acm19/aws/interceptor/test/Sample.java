package io.github.acm19.aws.interceptor.test;

import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

class Sample {
    static final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();

    public static void main(String[] args) throws IOException {
        Sample sampleClass = new Sample();
        sampleClass.makeGetRequest();
        sampleClass.makePostRequest();
    }

    private void makeGetRequest() throws IOException {
        HttpGet httpGet = new HttpGet("http://targethost/homepage");
        logRequest("", Region.US_EAST_1, httpGet);
    }

    private void makePostRequest() throws IOException {
        HttpPost httpPost = new HttpPost("http://targethost/login");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("username", "vip"));
        nvps.add(new BasicNameValuePair("password", "secret"));
        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        logRequest("", Region.US_EAST_1, httpPost);
    }

    void logRequest(String serviceName, Region region, HttpUriRequest request) throws IOException {
        System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
        CloseableHttpClient httpClient = signingClientForServiceName(serviceName, region);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            System.out.println(response.getStatusLine());
            String inputLine ;
            BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            try {
                while ((inputLine = br.readLine()) != null) {
                    System.out.println(inputLine);
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    CloseableHttpClient signingClientForServiceName(String serviceName, Region region) {
        Aws4Signer signer = Aws4Signer.create();

        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider, region);
        return HttpClients.custom()
                .addInterceptorLast(interceptor)
                .build();
    }

    HttpEntity stringEntity(final String body) throws UnsupportedEncodingException {
        BasicHttpEntity httpEntity = new BasicHttpEntity();
        httpEntity.setContent(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8.name())));
        return httpEntity;
    }
}
