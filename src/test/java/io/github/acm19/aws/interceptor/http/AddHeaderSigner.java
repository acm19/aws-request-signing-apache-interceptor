/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.auth.aws.internal.signer.DefaultAwsV4HttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignRequest;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignedRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.utils.IoUtils;

final class AddHeaderSigner implements AwsV4HttpSigner {
    private final AwsV4HttpSigner signer = new DefaultAwsV4HttpSigner();

    private final String name;
    private final String value;

    AddHeaderSigner(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public SignedRequest sign(SignRequest signRequest) {
        SdkHttpFullRequest.Builder request = SdkHttpFullRequest.builder()
                .uri(signRequest.request().getUri())
                .method(signRequest.request().method())
                .headers(signRequest.request().headers())
                .appendHeader(name, value)
                .appendHeader("resourcePath", signRequest.request().getUri().getRawPath());

        if (signRequest.payload().isPresent()) {
            ContentStreamProvider contentStreamProvider = (ContentStreamProvider) signRequest.payload().get();
            InputStream payloadStream = contentStreamProvider.newStream();
            ContentStreamProvider newContentStreamProvider = ContentStreamProvider.fromInputStream(payloadStream);

            request.contentStreamProvider(newContentStreamProvider)
                    .appendHeader("signedContentLength",
                            Long.toString(getContentLength(newContentStreamProvider.newStream())))
                    .build();
        }

        SdkHttpFullRequest requestBuilt = request.build();

        SignedRequest signedRequest =
            signer.sign(r -> r
                    .identity(AnonymousCredentialsProvider.create().resolveCredentials())
                    .request(requestBuilt)
                    .payload(requestBuilt.contentStreamProvider().orElse(null))
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "servicename")
                    .putProperty(AwsV4HttpSigner.REGION_NAME, "us-west-2")
                    .putProperty(AwsV4HttpSigner.DOUBLE_URL_ENCODE, false) // Required for S3 only
                    .putProperty(AwsV4HttpSigner.NORMALIZE_PATH, false)); // Required for S3 only

        return signedRequest;
    }

    private static int getContentLength(final InputStream content) {
        try {
            return IoUtils.toByteArray(content).length;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public CompletableFuture<AsyncSignedRequest> signAsync(AsyncSignRequest asyncSignRequest) {
        return null;
    }
}
