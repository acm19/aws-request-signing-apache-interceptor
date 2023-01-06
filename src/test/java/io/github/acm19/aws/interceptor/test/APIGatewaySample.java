/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.test;

import java.io.IOException;
import org.apache.commons.cli.ParseException;
import org.apache.http.client.methods.HttpGet;

public class APIGatewaySample extends Sample {
    APIGatewaySample(final String[] args) throws ParseException {
        super("execute-api", args);
    }

    /**
     *
     * @param args
     * @throws IOException
     * @throws ParseException
     */
    public static void main(String[] args) throws IOException, ParseException {
        APIGatewaySample apiGatewaySample = new APIGatewaySample(args);
        apiGatewaySample.makeAPIGGetRequest();
    }

    private void makeAPIGGetRequest() throws IOException {
        final HttpGet request = new HttpGet(endpoint + "/some/path?and=param");
        logRequest(request);
    }
}
