# AWS Request Signing Interceptor

[![tests](https://github.com/acm19/aws-request-signing-apache-interceptor/actions/workflows/test.yml/badge.svg)](https://github.com/acm19/aws-request-signing-apache-interceptor/actions/workflows/test.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.acm19/aws-request-signing-apache-interceptor)](https://search.maven.org/artifact/io.github.acm19/aws-request-signing-apache-interceptor)

An AWS request signing interceptor for arbitrary HTTP requests. It supports both [Apache HTTP Client](https://search.maven.org/artifact/org.apache.httpcomponents/httpclient) and [Apache HTTP Client V5](https://search.maven.org/artifact/org.apache.httpcomponents.client5/httpclient5).

This library enables you to sign requests to any service that leverages SigV4, and thus access any AWS Service or APIG-backed service.

This library is based on [AWS Interceptor](https://github.com/awslabs/aws-request-signing-apache-interceptor), but using AWS SDK 2.x.

## Usage

Add [io.github.acm19.aws-request-signing-apache-interceptor](https://repo1.maven.org/maven2/io/github/acm19/aws-request-signing-apache-interceptor/) as a dependency.

```xml
<dependency>
  <groupId>io.github.acm19</groupId>
  <artifactId>aws-request-signing-apache-interceptor</artifactId>
  <version>2.1.1</version>
</dependency>
```

### Apache HTTP Client

To sign requests made with pre-5 versions of the clients the following interceptor should be used `io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor`.

```java
import java.io.IOException;
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
import software.amazon.awssdk.utils.IoUtils;

final class Example {
    public static void main(String[] args) throws ClientProtocolException, IOException {
        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(
                "service",
                Aws4Signer.create(),
                DefaultCredentialsProvider.create(),
                Region.US_WEST_2
        );

        try (CloseableHttpClient client = HttpClients.custom()
                .addInterceptorLast(interceptor)
                .build()) {
            HttpGet httpGet = new HttpGet("https://...");
            CloseableHttpResponse response = client.execute(httpGet);
            System.out.println(response.getStatusLine());
            System.out.println(IoUtils.toUtf8String(response.getEntity().getContent()));
        }
    }
}
```

### Apache HTTP Client V5

To sign requests made with version 5 of the client the following interceptor should be used `io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor`.

```java
import java.io.IOException;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.IoUtils;

final class Example {
    public static void main(String[] args) throws ClientProtocolException, IOException {
        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "service",
                Aws4Signer.create(),
                DefaultCredentialsProvider.create(),
                Region.US_WEST_2
        );

        try (CloseableHttpClient client = HttpClients.custom()
                .addRequestInterceptorLast(interceptor)
                .build()) {
            HttpGet httpGet = new HttpGet("https://...");
            CloseableHttpResponse response = client.execute(httpGet);
            System.out.println(response.getCode());
            System.out.println(IoUtils.toUtf8String(response.getEntity().getContent()));
        }
    }
}
```

## Examples

To run the [Amazon OpenSearch Sample](src/test/java/io/github/acm19/aws/interceptor/test/AmazonOpenSearchServiceSample.java) pass the values of _endpoint_ and _region_ into `exec.args`.

```
export AWS_ACCESS_KEY_ID=
export AWS_SECRET_ACCESS_KEY=
export AWS_SESSION_TOKEN=

mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass="io.github.acm19.aws.interceptor.test.AmazonOpenSearchServiceSample" -Dexec.args="--endpoint=https://...us-west-2.es.amazonaws.com --region=us-west-2"
```

Alternatively use `make` as follows:

```
ENDPOINT=<your-endpoint> REGION=<your-region> make run_sample
```

See [examples](src/test/java/io/github/acm19/aws/interceptor/test) for more valid requests.

## Contributing

You're encouraged to contribute to this project. See [CONTRIBUTING](CONTRIBUTING.md) for details.

## Copyright

Copyright Amazon.com, Inc. or its affiliates, and Project Contributors.
See [NOTICE](NOTICE) for details.

## License

This library is licensed under the [Apache 2.0 License](LICENSE).
