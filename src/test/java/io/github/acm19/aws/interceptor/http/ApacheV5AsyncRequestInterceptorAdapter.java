/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.IOException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ClassicHttpRequest;

class ApacheV5AsyncRequestInterceptorAdapter implements ApacheV5ClientAdapter {
    private final CloseableHttpAsyncClient client;

    ApacheV5AsyncRequestInterceptorAdapter(AwsRequestSigningApacheV5Interceptor interceptor) {
        this.client = HttpAsyncClients.custom().addRequestInterceptorLast(interceptor).build();
        this.client.start();
    }

    @Override
    public void execute(ClassicHttpRequest request) throws Exception {
        SimpleHttpRequest sr = SimpleRequestBuilder.copy(request).build();
        client.execute(sr, null).get();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
