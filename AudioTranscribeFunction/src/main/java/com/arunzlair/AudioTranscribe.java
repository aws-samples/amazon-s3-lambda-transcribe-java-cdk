package com.arunzlair;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FilenameUtils;
import software.amazon.awssdk.services.transcribe.TranscribeAsyncClient;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.Transcript;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AudioTranscribe implements RequestHandler<S3Event, String> {
	private final Gson gson = new GsonBuilder().setPrettyPrinting()
	                                           .create();

	private final TranscribeAsyncClient transcribeAsyncClient = TranscribeAsyncClient.builder()
	                                                                                 .build();

	private final List<String> supportedExtensions = List.of("amr", "flac", "m4a", "mp3", "mp4", "ogg", "webm", "wav");

	@Override
	public String handleRequest(S3Event event, Context context) {
		LambdaLogger logger = context.getLogger();
		String response = "200 OK";

		String languageCode = System.getenv("LANGUAGE_CODE");
		String outputBucket = System.getenv("OUTPUT_BUCKET");

		S3EventNotification.S3EventNotificationRecord s3EventNotificationRecord = event.getRecords()
		                                                                               .get(0);
		String bucketName = s3EventNotificationRecord.getS3()
		                                             .getBucket()
		                                             .getName();
		String key = s3EventNotificationRecord.getS3()
		                                      .getObject()
		                                      .getKey();
		String awsRequestId = context.getAwsRequestId();

		String extension = FilenameUtils.getExtension(key);


		if (!supportedExtensions.contains(extension.toLowerCase())) {
			throw new RuntimeException("Invalid file extension, unsupported Amazon Transcribe file types!");
		}

		CompletableFuture<StartTranscriptionJobResponse> startTranscriptionJobResponseCompletableFuture = transcribeAsyncClient.startTranscriptionJob(StartTranscriptionJobRequest.builder()
		                                                                                                                                                                          .transcriptionJobName("s3-lambda-audio-transcribe-" + awsRequestId)
		                                                                                                                                                                          .languageCode(languageCode)
		                                                                                                                                                                          .media(Media.builder()
		                                                                                                                                                                                      .mediaFileUri("s3://" + bucketName + "/" + key)
		                                                                                                                                                                                      .build())
		                                                                                                                                                                          .mediaFormat(extension)
		                                                                                                                                                                          .outputBucketName(outputBucket)
		                                                                                                                                                                          .build());

		logger.log("Started the transcription job");

		try {
			StartTranscriptionJobResponse startTranscriptionJobResponse = startTranscriptionJobResponseCompletableFuture.get();
			Transcript transcript = startTranscriptionJobResponse.transcriptionJob()
			                                                     .transcript();
			System.out.println("startTranscriptionJobResponse = " + transcript);
			logger.log("Completed the transcription job");
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
		return response;
	}
}