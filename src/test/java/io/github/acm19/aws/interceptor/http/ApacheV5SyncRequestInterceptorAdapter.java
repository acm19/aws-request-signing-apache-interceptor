/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.IOException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;

class ApacheV5SyncRequestInterceptorAdapter implements ApacheV5ClientAdapter {
    private final CloseableHttpClient client;

    ApacheV5SyncRequestInterceptorAdapter(AwsRequestSigningApacheV5Interceptor interceptor) {
        this.client = HttpClients.custom().addRequestInterceptorLast(interceptor).build();
    }

    @Override
    public void execute(ClassicHttpRequest request) throws Exception {
        client.execute(request);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
