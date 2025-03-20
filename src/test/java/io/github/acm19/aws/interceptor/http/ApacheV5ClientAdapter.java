/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.http;

import java.io.Closeable;
import org.apache.hc.core5.http.ClassicHttpRequest;

interface ApacheV5ClientAdapter extends Closeable {
    void execute(ClassicHttpRequest request) throws Exception;
}
