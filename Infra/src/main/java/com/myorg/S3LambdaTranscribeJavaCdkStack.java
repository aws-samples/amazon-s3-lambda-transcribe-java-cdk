package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
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

import java.util.Map;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class S3LambdaTranscribeJavaCdkStack extends Stack {
	public S3LambdaTranscribeJavaCdkStack(final Construct scope, final String id) {
		this(scope, id, null);
	}

	public S3LambdaTranscribeJavaCdkStack(final Construct scope, final String id, final StackProps props) {
		super(scope, id, props);

		Bucket loggingBucket = Bucket.Builder.create(this, "LoggingBucket")
		                                     .enforceSsl(true)
		                                     .encryption(BucketEncryption.KMS)
		                                     .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
		                                     .versioned(true)
		                                     .build();

		Bucket sourceBucket = Bucket.Builder.create(this, "SourceBucket")
		                                    .enforceSsl(true)
		                                    .versioned(true)
		                                    .encryption(BucketEncryption.KMS)
		                                    .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
		                                    .serverAccessLogsBucket(loggingBucket)
		                                    .serverAccessLogsPrefix("/sourceBucket")
		                                    .build();

		Bucket destinationBucket = Bucket.Builder.create(this, "DestinationBucket")
		                                         .enforceSsl(true)
		                                         .versioned(true)
		                                         .encryption(BucketEncryption.KMS)
		                                         .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
		                                         .serverAccessLogsBucket(loggingBucket)
		                                         .serverAccessLogsPrefix("/destinationBucket")
		                                         .build();

		// Create an IAM role for the Lambda function
		Role lambdaRole = Role.Builder.create(this, "LambdaRole")
		                              .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
		                              .build();

		// Attach the necessary policies to the Lambda role
		lambdaRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonTranscribeFullAccess"));


		Function audioTranscribeFunction = Function.Builder.create(this, "AudioTranscribe")
		                                                   .runtime(Runtime.JAVA_11)
		                                                   .architecture(Architecture.X86_64)
		                                                   .handler("com.arunzlair.AudioTranscribe")
		                                                   .memorySize(1024)
		                                                   .timeout(Duration.minutes(3))
		                                                   .code(Code.fromAsset("../assets/AudioTranscribeFunction.jar"))
		                                                   .environment(Map.of("LANGUAGE_CODE", "US_EN", "OUTPUT_BUCKET", destinationBucket.getBucketName()))
		                                                   .role(lambdaRole)
		                                                   .build();


		// Add Object Created Notification to Source Bucket
		sourceBucket.addObjectCreatedNotification(new LambdaDestination(audioTranscribeFunction));

		sourceBucket.grantReadWrite(audioTranscribeFunction);
		destinationBucket.grantReadWrite(audioTranscribeFunction);
	}
}
