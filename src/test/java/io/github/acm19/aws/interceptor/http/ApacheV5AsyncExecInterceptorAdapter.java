/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.IOException;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

class ApacheV5AsyncExecInterceptorAdapter implements ApacheV5ClientAdapter {
    private final CloseableHttpAsyncClient client;

    ApacheV5AsyncExecInterceptorAdapter(AwsRequestSigningApacheV5ExecInterceptor interceptor) {
        this.client = HttpAsyncClients.custom().addExecInterceptorLast("custom", interceptor).build();
        this.client.start();
    }

    @Override
    public void execute(ClassicHttpRequest request) throws Exception {
        SimpleRequestBuilder requestBuilder = SimpleRequestBuilder.copy(request);
        HttpEntity entity = request.getEntity();
        if (entity != null) {
            requestBuilder.setBody(entity.getContent().readAllBytes(), ContentType.parse(entity.getContentType()));
        }
        client.execute(requestBuilder.build(), null).get();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
