/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptorv5.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.cli.ParseException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.GzipCompressingEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * An AWS Request Signing Interceptor sample for arbitrary HTTP requests to an
 * Amazon OpenSearch Service domain.
 *
 * In addition to the examples below that use Apache Http, the interceptor can
 * also be used with the OpenSearch REST clients for additional convenience and
 * serialization.
 *
 * Example usage with the OpenSearch low-level REST client:
 *
 * <pre>
 * AwsRequestSigningApacheInterceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
 *         "es",
 *         AwsV4HttpSigner.create(),
 *         DefaultCredentialsProvider.create(),
 *         "us-east-1");
 *
 * return RestClient
 *         .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *         .setHttpClientConfigCallback(hacb -> hacb.addExecInterceptorLast("aws-signing-interceptor", interceptor))
 *         .build();
 * </pre>
 *
 * Example usage with the OpenSearch high-level REST client:
 *
 * <pre>
 * AwsRequestSigningApacheInterceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
 *         "es",
 *         AwsV4HttpSigner.create(),
 *         DefaultCredentialsProvider.create(),
 *         "us-east-1");
 *
 * return new RestHighLevelClient(
 *         RestClient
 *                 .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *                 .setHttpClientConfigCallback(hacb ->
 *                     hacb.addExecInterceptorLast("aws-signing-interceptor", interceptor)));
 * </pre>
 */
public final class AmazonOpenSearchServiceSample extends Sample {
    private AmazonOpenSearchServiceSample(String[] args) throws ParseException {
        super(args);
    }

    /**
     *
     * @param args
     * @throws IOException
     * @throws ParseException
     */
    public static void main(String[] args) throws IOException, ParseException {
        AmazonOpenSearchServiceSample sample = new AmazonOpenSearchServiceSample(args);
        sample.makeRequest();
        sample.createIndex();
        try {
            sample.indexDocument();
            sample.indexDocumentWithCompressionEnabled();
            sample.indexDocumentAsynchronously();
            // https://github.com/acm19/aws-request-signing-apache-interceptor/issues/20
            // sample.indexDocumentWithChunkedTransferEncoding();
            // sample.indexDocumentWithChunkedTransferEncodingCompressionEnabled();
        } finally {
            sample.deleteIndex();
        }
    }

    private void createIndex() throws IOException {
        String payload = "{}";
        HttpPut httpPut = new HttpPut(endpoint + "/index_name");
        httpPut.setEntity(new StringEntity(payload));
        httpPut.addHeader("Content-Type", "application/json");
        logRequest(httpPut);
    }

    private void deleteIndex() throws IOException {
        HttpDelete httpDelete = new HttpDelete(endpoint + "/index_name");
        logRequest(httpDelete);
    }

    private void makeRequest() throws IOException {
        // Remove when GET support is added to OpenSearch Serverless:
        // https://github.com/acm19/aws-request-signing-apache-interceptor/issues/95
        if (!service.equals("aoss")) {
            logRequest(new HttpGet(endpoint));
        }
    }

    private void indexDocument() throws IOException {
        String payload = "{\"test\": \"val\"}";
        HttpPost httpPost = new HttpPost(endpoint + "/index_name/_doc");
        httpPost.setEntity(new StringEntity(payload));
        httpPost.addHeader("Content-Type", "application/json");
        logRequest(httpPost);
    }

    private void indexDocumentWithChunkedTransferEncoding() throws IOException {
        String payload = "{\"test\": \"val\"}";
        HttpPost httpPost = new HttpPost(endpoint + "/index_name/_doc");
        StringEntity entity = new StringEntity(payload, ContentType.DEFAULT_TEXT, true);
        httpPost.setEntity(entity);
        httpPost.addHeader("Content-Type", "application/json");
        logRequest(httpPost);
    }

    private void indexDocumentWithCompressionEnabled() throws IOException {
        HttpPost httpPost = new HttpPost(endpoint + "/index_name/_doc");
        httpPost.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        httpPost.addHeader("Content-Type", "application/json");
        String payload = "{\"test\": \"val\"}";
        // do not use GZipCompressingEntity because it forces chunking
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(payload.getBytes("UTF-8"));
        }
        ByteArrayEntity entity = new ByteArrayEntity(outputStream.toByteArray(), ContentType.DEFAULT_BINARY, "gzip");
        httpPost.setEntity(entity);
        logRequest(httpPost);
    }

    private void indexDocumentWithChunkedTransferEncodingCompressionEnabled() throws IOException {
        HttpPost httpPost = new HttpPost(endpoint + "/index_name/_doc");
        httpPost.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        httpPost.addHeader("Content-Type", "application/json");
        String payload = "{\"test\": \"val\"}";
        // chunked by default
        GzipCompressingEntity entity = new GzipCompressingEntity(new StringEntity(payload));
        httpPost.setEntity(entity);
        logRequest(httpPost);
    }

    private void indexDocumentAsynchronously() throws IOException {
        String payload = "{\"test\": \"ayncVal\"}";
        SimpleHttpRequest httpPost = SimpleRequestBuilder.post()
                .setUri(endpoint + "/index_name/_doc")
                .setBody(payload, ContentType.APPLICATION_JSON)
                .build();

        logAsyncRequest(httpPost);
    }
}
