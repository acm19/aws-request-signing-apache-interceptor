package io.github.acm19.aws.interceptor.test;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import java.io.IOException;

/**
 * <p>An AWS Request Signing Interceptor sample for arbitrary HTTP requests to an Amazon Elasticsearch Service domain.</p>
 * <p>The interceptor can also be used with the Elasticsearch REST clients for additional convenience and serialization.</p>
 * <p>Example usage with the Elasticsearch low-level REST client:</p>
 * <pre>
 * String serviceName = "es";
 * Aws4Signer signer = Aws4Signer.create();
 *
 * HttpRequestInterceptor interceptor =
 *     new AwsRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider, "us-east-1");
 *
 * return RestClient
 *     .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *     .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor))
 *     .build();
 * </pre>
 * <p>Example usage with the Elasticsearch high-level REST client:</p>
 * <pre>
 * String serviceName = "es";
 * Aws4Signer signer = Aws4Signer.create();
 *
 * HttpRequestInterceptor interceptor =
 *     new AwsRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider, "us-east-1");
 *
 * return new RestHighLevelClient(RestClient
 *     .builder(HttpHost.create("https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com"))
 *     .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor)));
 * </pre>
 */
public class AmazonElasticsearchServiceSample extends Sample {
    private static final String AES_ENDPOINT = "https://search-my-es-endpoint-gjhfgfhgfhg.us-east-1.amazonaws.com";
    public static void main(String[] args) throws IOException {
        AmazonElasticsearchServiceSample aesSample = new AmazonElasticsearchServiceSample();
        aesSample.makeAESRequest();
        aesSample.indexDocument();
    }

    private void makeAESRequest() throws IOException {
        HttpGet httpGet = new HttpGet(AES_ENDPOINT);
        logRequest("es", httpGet);
    }

    private void indexDocument() throws IOException {
        String payload = "{\"test\": \"val\"}";
        HttpPost httpPost = new HttpPost(AES_ENDPOINT + "/index_name/type_name/document_id");
        httpPost.setEntity(stringEntity(payload));
        httpPost.addHeader("Content-Type", "application/json");
        logRequest("es", httpPost);
    }
}
