- [Upgrading AWS Request Signing Interceptor](#upgrading-opensearch-python-client)
    - [## Upgrading to >= 3.0.0](#upgrading-to->=-3.0.0)


# Upgrading AWS Request Signing Interceptor

## Upgrading to >= 3.0.0

`Aws4Signer` has been deprecated in the AWS SDK for Java 2.0 and replaced with `AwsV4HttpSigner`.

As a consequence, the `AwsRequestSigningApacheInterceptor` and `AwsRequestSigningApacheV5Interceptor` now requires an `AwsV4HttpSigner` instance instead of an `Aws4Signer`.

To upgrade, replace the `Aws4Signer` with `AwsV4HttpSigner` in your code. For example, if you are using `AwsRequestSigningApacheInterceptor`:

```java
import java.io.IOException;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

final class Example {
    public static void main(String[] args) throws ClientProtocolException, IOException {
        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "service",
                Aws4Signer.create(),
                DefaultCredentialsProvider.create(),
                Region.US_WEST_2
        );
    }
}
```

becomes:

```java
import java.io.IOException;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheV5Interceptor;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

final class Example {
    public static void main(String[] args) throws ClientProtocolException, IOException {
        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheV5Interceptor(
                "service",
                AwsV4HttpSigner.create(),
                DefaultCredentialsProvider.create(),
                Region.US_WEST_2
        );
    }
}
```

