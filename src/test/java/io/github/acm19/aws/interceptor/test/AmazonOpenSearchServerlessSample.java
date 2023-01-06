/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.cli.ParseException;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import software.amazon.awssdk.auth.signer.Aws4UnsignedPayloadSigner;

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
 * HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(
 *         "aoss",
 *         Aws4UnsignedPayloadSigner.create(),
 *         DefaultCredentialsProvider.create(),
 *         "us-east-1");
 *
 * return RestClient
 *         .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *         .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor))
 *         .build();
 * </pre>
 *
 * Example usage with the OpenSearch high-level REST client:
 *
 * <pre>
 * HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(
 *         "aoss",
 *         Aws4UnsignedPayloadSigner.create(),
 *         DefaultCredentialsProvider.create(),
 *         "us-east-1");
 *
 * return new RestHighLevelClient(
 *         RestClient
 *                 .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *                 .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor)));
 * </pre>
 */
public class AmazonOpenSearchServerlessSample extends Sample {
    AmazonOpenSearchServerlessSample(final String[] args) throws ParseException {
        super("aoss", args, Aws4UnsignedPayloadSigner.create());
    }

    /**
     *
     * @param args
     * @throws IOException
     * @throws ParseException
     */
    public static void main(String[] args) throws IOException, ParseException {
        AmazonOpenSearchServerlessSample sample = new AmazonOpenSearchServerlessSample(args);
        sample.indexDocument();
        sample.indexDocumentWithCompressionEnabled();
    }

    private void indexDocument() throws IOException {
        String payload = "{\"test\": \"val\"}";
        HttpPost httpPost = new HttpPost(endpoint + "/index_name/_doc");
        httpPost.setEntity(new StringEntity(payload));
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
        ByteArrayEntity entity = new ByteArrayEntity(outputStream.toByteArray(), ContentType.DEFAULT_BINARY);
        entity.setContentEncoding("gzip");
        httpPost.setEntity(entity);
        logRequest(httpPost);
    }
}
