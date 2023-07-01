/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 */

package com.amazonaws.lambda;

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

import java.util.List;
import java.util.UUID;
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
        String lineSeparator = System.lineSeparator();
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

        if (languageCode.contains(",")) {
            for (String language : languageCode.split(",")) {
                logger.log("Starting to process file " + key + " for language codes " + languageCode + lineSeparator);
                runTranscribeJob(logger, language, outputBucket, bucketName, key, awsRequestId, extension, lineSeparator);
                logger.log("Completed the processing of file " + key + " for language codes " + languageCode + lineSeparator);
            }

        } else {
            logger.log("Starting to process file " + key + " for language code " + languageCode + lineSeparator);
            runTranscribeJob(logger, languageCode, outputBucket, bucketName, key, awsRequestId, extension, lineSeparator);
            logger.log("Completed the processing of file " + key + " for language code " + languageCode + lineSeparator);
        }

        return response;
    }

    private void runTranscribeJob(LambdaLogger logger, String languageCode, String outputBucket, String bucketName, String key, String awsRequestId, String extension, String lineSeparator) {
        logger.log("Initializing the transcription job for language code " + languageCode + lineSeparator);
        CompletableFuture<StartTranscriptionJobResponse> startTranscriptionJobResponseCompletableFuture = transcribeAsyncClient.startTranscriptionJob(StartTranscriptionJobRequest.builder()
                .transcriptionJobName("s3-lambda-audio-transcribe-" + UUID.randomUUID())
                .languageCode(languageCode)
                .media(Media.builder()
                        .mediaFileUri("s3://" + bucketName + "/" + key)
                        .build())
                .mediaFormat(extension)
                .outputBucketName(outputBucket)
                .outputKey(key + "_Transcription_" + languageCode+".json")
                .outputEncryptionKMSKeyId(System.getenv("DEST_KEY_ID"))
                .build());

        logger.log("Started the transcription job request for language code " + languageCode + lineSeparator);

        try {
            StartTranscriptionJobResponse startTranscriptionJobResponse = startTranscriptionJobResponseCompletableFuture.get();
            System.out.println("startTranscriptionJobResponse = " + startTranscriptionJobResponse);
            logger.log("Completed the transcription job request for language code " + languageCode + lineSeparator);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}