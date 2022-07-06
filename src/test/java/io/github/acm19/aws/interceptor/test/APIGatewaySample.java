/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The AWS Request Signing Interceptor Contributors require
 * contributions made to this file be licensed under the
 * Apache-2.0 license or a compatible open source license.
 */

package io.github.acm19.aws.interceptor.test;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;

import software.amazon.awssdk.regions.Region;

public class APIGatewaySample extends Sample {
    /**
     * The invoke URL for your API which is usually
     * https://api_id.execute-api.api-region.amazonaws.com/stage
     */
    private static final String INVOKE_URL = "https://api_id.execute-api.api-region.amazonaws.com/stage";
    private static final Region REGION = Region.US_EAST_1;

    public static void main(String[] args) throws IOException {
        APIGatewaySample apiGatewaySample = new APIGatewaySample();
        apiGatewaySample.makeAPIGGetRequest();
    }

    private void makeAPIGGetRequest() throws IOException {
        HttpGet Http = new HttpGet(INVOKE_URL + "/some/path?and=param");
        logRequest("execute-api", REGION, Http);
    }
}
