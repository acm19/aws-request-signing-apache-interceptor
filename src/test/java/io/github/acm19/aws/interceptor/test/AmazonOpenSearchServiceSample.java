package io.github.acm19.aws.interceptor.test;

import java.io.IOException;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;

import software.amazon.awssdk.regions.Region;

/**
 * <p>
 * An AWS Request Signing Interceptor sample for arbitrary HTTP requests to an
 * Amazon OpenSearch Service domain.
 * </p>
 * <p>
 * The interceptor can also be used with the OpenSearch REST clients for
 * additional convenience and serialization.
 * </p>
 * <p>
 * Example usage with the OpenSearch low-level REST client:
 * </p>
 * 
 * <pre>
 * String serviceName = "es";
 * Aws4Signer signer = Aws4Signer.create();
 *
 * HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider,
 *         "us-east-1");
 *
 * return RestClient
 *         .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *         .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor))
 *         .build();
 * </pre>
 * <p>
 * Example usage with the OpenSearch high-level REST client:
 * </p>
 * 
 * <pre>
 * String serviceName = "es";
 * Aws4Signer signer = Aws4Signer.create();
 *
 * HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider,
 *         "us-east-1");
 *
 * return new RestHighLevelClient(RestClient
 *         .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *         .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor)));
 * </pre>
 */
public class AmazonOpenSearchServiceSample extends Sample {
    private static final String ENDPOINT = "https://search-dblock-test-opensearch-21-tu5gqrjd4vg4qazjsu6bps5zsy.us-west-2.es.amazonaws.com";
    private static final Region REGION = Region.US_WEST_2;

    public static void main(String[] args) throws IOException {
        AmazonOpenSearchServiceSample sample = new AmazonOpenSearchServiceSample();
        sample.makeRequest();
        sample.indexDocument();
        sample.indexDocumentWithCompressionEnabled();
        // https://github.com/acm19/aws-request-signing-apache-interceptor/issues/20
        // sample.indexDocumentWithChunkedTransferEncoding();
        // sample.indexDocumentWithChunkedTransferEncodingCompressionEnabled();
    }

    private void makeRequest() throws IOException {
        HttpGet httpGet = new HttpGet(ENDPOINT);
        logRequest("es", REGION, httpGet);
    }

    private void indexDocument() throws IOException {
        String payload = "{\"test\": \"val\"}";
        HttpPost httpPost = new HttpPost(ENDPOINT + "/index_name/type_name/document_id");
        httpPost.setEntity(stringEntity(payload));
        httpPost.addHeader("Content-Type", "application/json");
        logRequest("es", REGION, httpPost);
    }

    private void indexDocumentWithChunkedTransferEncoding() throws IOException {
        String payload = "{\"test\": \"val\"}";
        HttpPost httpPost = new HttpPost(ENDPOINT + "/index_name/type_name/document_id");
        BasicHttpEntity entity = stringEntity(payload);
        entity.setChunked(true);
        entity.setContentLength(-1L);
        httpPost.setEntity(entity);
        httpPost.addHeader("Content-Type", "application/json");
        logRequest("es", REGION, httpPost);
    }

    private void indexDocumentWithCompressionEnabled() throws IOException {
        HttpPost httpPost = new HttpPost(ENDPOINT + "/index_name/type_name/document_id");
        httpPost.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        httpPost.addHeader("Content-Type", "application/json");
        String payload = "{\"test\": \"val\"}";
        httpPost.setEntity(gzipEntity(payload));
        logRequest("es", REGION, httpPost);
    }

    private void indexDocumentWithChunkedTransferEncodingCompressionEnabled() throws IOException {
        HttpPost httpPost = new HttpPost(ENDPOINT + "/index_name/type_name/document_id");
        httpPost.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        httpPost.addHeader("Content-Type", "application/json");
        String payload = "{\"test\": \"val\"}";
        ByteArrayEntity entity = gzipEntity(payload);
        entity.setChunked(true);
        httpPost.setEntity(entity);
        logRequest("es", REGION, httpPost);
    }
}
