# AWS Request Signing Interceptor

[![tests](https://github.com/acm19/aws-request-signing-apache-interceptor/actions/workflows/test.yml/badge.svg)](https://github.com/acm19/aws-request-signing-apache-interceptor/actions/workflows/test.yml)

An AWS request signing interceptor for arbitrary HTTP requests.

This library enables you to sign requests to any service that leverages SigV4, and thus access any AWS Service or APIG-backed service.

This library is based on [AWS Interceptor](https://github.com/awslabs/aws-request-signing-apache-interceptor), but using AWS SDK 2.x.

## Usage

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

public static void main(String[] args) throws ClientProtocolException, IOException {
        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(
                "service",
                Aws4Signer.create(), 
                DefaultCredentialsProvider.create(), 
                Region.US_WEST_2
        );

        CloseableHttpClient client = HttpClients.custom()
                .addInterceptorLast(interceptor)
                .build();

        HttpGet httpGet = new HttpGet("https://...");
        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
        System.out.println(httpResponse.getStatusLine());

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        httpResponse.getEntity().getContent()
                )
        );

        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
        }
        reader.close();

        System.out.println(response.toString());
        httpClient.close();
}
```

## Examples

To run the [Amazon OpenSearch Sample](src/test/java/io/github/acm19/aws/interceptor/test/AmazonOpenSearchServiceSample.java), replace the values of `host` and `region` in the source and run the following. 

```
export AWS_ACCESS_KEY_ID=
export AWS_SECRET_ACCESS_KEY=
export AWS_SESSION_TOKEN=

mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass="io.github.acm19.aws.interceptor.test.AmazonOpenSearchServiceSample"
```

See [examples](src/test/java/io/github/acm19/aws/interceptor/test) for more valid requests. 

## Contributing 

You're encouraged to contribute to this project. See [CONTRIBUTING](CONTRIBUTING.md) for details.

## Copyright

Copyright Amazon.com, Inc. or its affiliates, and Project Contributors.
See [NOTICE](NOTICE) for details.

## License

This library is licensed under the [Apache 2.0 License](LICENSE).
