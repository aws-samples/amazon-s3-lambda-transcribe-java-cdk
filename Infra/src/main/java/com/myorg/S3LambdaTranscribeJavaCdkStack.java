package com.myorg;

import io.github.cdklabs.cdknag.NagPackSuppression;
import io.github.cdklabs.cdknag.NagSuppressions;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.notifications.LambdaDestination;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class S3LambdaTranscribeJavaCdkStack extends Stack {
    public S3LambdaTranscribeJavaCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public S3LambdaTranscribeJavaCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

//		CfnParameter sourceBucketName = CfnParameter.Builder.create(this, "SourceBucketName")
//		                                                    .type("String")
//		                                                    .description("Name of the source bucket")
//		                                                    .build();
//		CfnParameter destinationBucketName = CfnParameter.Builder.create(this, "DestinationBucketName")
//		                                                         .type("String")
//		                                                         .description("Name of the destination bucket")
//		                                                         .build();

        CfnParameter languageCode = CfnParameter.Builder.create(this, "transcribeLanguageCode")
                .type("String")
                .description("Language code for the transcription")
                .defaultValue("es-US,en-US")
                .build();


        Bucket loggingBucket = Bucket.Builder.create(this, "LoggingBucket")
                .enforceSsl(true)
                .encryption(BucketEncryption.KMS)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .versioned(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();


        Bucket sourceBucket = Bucket.Builder.create(this, "SourceBucket")
                .enforceSsl(true)
                .versioned(true)
                .encryption(BucketEncryption.KMS)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .serverAccessLogsBucket(loggingBucket)
                .serverAccessLogsPrefix("sourceBucket/")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Bucket destinationBucket = Bucket.Builder.create(this, "DestinationBucket")
                .enforceSsl(true)
                .versioned(true)
                .encryption(BucketEncryption.KMS)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .serverAccessLogsBucket(loggingBucket)
                .serverAccessLogsPrefix("destinationBucket/")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Create an IAM role for the Lambda function
        Role lambdaRole = Role.Builder.create(this, "LambdaRole")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .build();

        // Attach the necessary policies to the Lambda role
        lambdaRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonTranscribeFullAccess"));
        lambdaRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("CloudWatchLogsFullAccess"));


        Function audioTranscribeFunction = Function.Builder.create(this, "AudioTranscribe")
                .runtime(Runtime.JAVA_11)
                .architecture(Architecture.X86_64)
                .handler("com.arunzlair.AudioTranscribe")
                .memorySize(1024)
                .timeout(Duration.minutes(3))
                .code(Code.fromAsset("../assets/AudioTranscribeFunction.jar"))
                .environment(Map.of("LANGUAGE_CODE", languageCode.getValueAsString(),
                        "OUTPUT_BUCKET", destinationBucket.getBucketName()))
                .role(lambdaRole)

                .build();


        // Add Object Created Notification to Source Bucket
        sourceBucket.addObjectCreatedNotification(new LambdaDestination(audioTranscribeFunction));

        sourceBucket.grantReadWrite(audioTranscribeFunction);
        destinationBucket.grantReadWrite(audioTranscribeFunction);

        //CDK NAG Suppression's
        NagSuppressions.addStackSuppressions(this, List.of(NagPackSuppression.builder()
                .id("AwsSolutions-KMS5")
                .reason("S3 Bucket for this example doesn't require KMS rotation needed. In the production, make sure this is enabled for the KMS used in the S3 Buckets")
                .build()));

        NagSuppressions.addStackSuppressions(this, List.of(NagPackSuppression.builder()
                .id("AwsSolutions-IAM4")
                .reason("AWS managed policies acceptable for this Sample use case")
                .build()));

        NagSuppressions.addStackSuppressions(this, List.of(NagPackSuppression.builder()
                .id("AwsSolutions-IAM5")
                .reason("The IAM entity in this example contain wildcard permissions. In a real world production workload it is recommended adhering to AWS security best practices regarding least-privilege permissions (https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html#grant-least-privilege)")
                .build()));
        NagSuppressions.addStackSuppressions(this, List.of(NagPackSuppression.builder()
                .id("AwsSolutions-L1")
                .reason("Java 11 is LTS version")
                .build()));

    }
}
