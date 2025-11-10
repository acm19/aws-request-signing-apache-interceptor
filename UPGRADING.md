- [Upgrading AWS Request Signing Interceptor](#upgrading-opensearch-python-client)
    - [## Upgrading to >= 4.0.0](#upgrading-to->=-4.0.0)
    - [## Upgrading to >= 3.0.0](#upgrading-to->=-3.0.0)


# Upgrading AWS Request Signing Interceptor

## Upgrading to >= 4.0.0

The `AwsRequestSigningApacheV5Interceptor` was migrated from `HttpRequestInterceptor` to `ExecChainHandler` and `AsyncExecChainHandler` to support async requests.
As a consequence, the `AwsRequestSigningApacheV5Interceptor` now needs to be registered as an "exec" interceptor instead of a "request" interceptor.

To upgrade, replace `.addRequestInterceptorLast(interceptor)` with `.addExecInterceptorLast("aws-request-signer", interceptor)` in your code.

```java
import java.io.IOException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import org.apache.http.HttpRequestInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.IoUtils;

final class Example {
    public static void main(String[] args) throws IOException {
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "service",
                AwsV4HttpSigner.create(),
                DefaultCredentialsProvider.builder().build(),
                Region.US_WEST_2
        );

        try (CloseableHttpClient client = HttpClients.custom()
                .addRequestInterceptorLast(interceptor)
                .build()) {
            HttpGet httpGet = new HttpGet("https://...");
            client.execute(httpGet, response -> {
                System.out.println(response.getCode());
                System.out.println(IoUtils.toUtf8String(response.getEntity().getContent()));
            });
        }
    }
}
```

becomes:

```java
import java.io.IOException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.IoUtils;

final class Example {
    public static void main(String[] args) throws IOException {
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "service",
                AwsV4HttpSigner.create(),
                DefaultCredentialsProvider.builder().build(),
                Region.US_WEST_2
        );

        try (CloseableHttpClient client = HttpClients.custom()
                .addExecInterceptorLast("aws-signing-interceptor", interceptor)
                .build()) {
            HttpGet httpGet = new HttpGet("https://...");
            client.execute(httpGet, response -> {
                System.out.println(response.getCode());
                System.out.println(IoUtils.toUtf8String(response.getEntity().getContent()));
            });
        }
    }
}
```

## Upgrading to >= 3.0.0

`Aws4Signer` has been deprecated in the AWS SDK for Java 2.0 and replaced with `AwsV4HttpSigner`.

As a consequence, the `AwsRequestSigningApacheInterceptor` and `AwsRequestSigningApacheV5Interceptor` now requires an `AwsV4HttpSigner` instance instead of an `Aws4Signer`.

To upgrade, replace the `Aws4Signer` with `AwsV4HttpSigner` in your code. For example, if you are using `AwsRequestSigningApacheInterceptor`:

```java
import java.io.IOException;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

final class Example {
    public static void main(String[] args) throws IOException {
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "service",
                Aws4Signer.create(),
                DefaultCredentialsProvider.builder().create(),
                Region.US_WEST_2
        );
    }
}
```

becomes:

```java
import java.io.IOException;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

final class Example {
    public static void main(String[] args) throws IOException {
        AwsRequestSigningApacheV5Interceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "service",
                AwsV4HttpSigner.create(),
                DefaultCredentialsProvider.builder().create(),
                Region.US_WEST_2
        );
    }
}
```
