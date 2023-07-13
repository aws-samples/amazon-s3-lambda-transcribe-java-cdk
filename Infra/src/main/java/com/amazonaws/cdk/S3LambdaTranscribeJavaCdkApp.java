/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 */

package com.amazonaws.cdk;

import io.github.cdklabs.cdknag.AwsSolutionsChecks;
import io.github.cdklabs.cdknag.NagPackProps;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Aspects;
import software.amazon.awscdk.StackProps;

public class S3LambdaTranscribeJavaCdkApp {
    public static void main(final String[] args) {
        App app = new App();
        Aspects.of(app).add(new AwsSolutionsChecks(NagPackProps.builder()
                .verbose(true)
                .build()));
        new S3LambdaTranscribeJavaCdkStack(app, "S3LambdaTranscribeJavaCdkStack", StackProps.builder()
                .build());

        app.synth();
    }
}

