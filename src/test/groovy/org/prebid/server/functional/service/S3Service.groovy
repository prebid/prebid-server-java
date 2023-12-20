package org.prebid.server.functional.service

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.core.sync.RequestBody

final class S3Service implements ObjectMapperWrapper {

    private final S3Client s3PbsService
    private final LocalStackContainer localStackContainer = Dependencies.localStackContainer

    static final def DEFAULT_ACCOUNT_DIR = 'account'
    static final def DEFAULT_IMPS_DIR = 'stored-impressions'
    static final def DEFAULT_REQUEST_DIR = 'stored-requests'
    static final def DEFAULT_RESPONSE_DIR = 'stored-responses'

    S3Service(String bucketName) {
        s3PbsService = S3Client.builder()
                .endpointOverride(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        localStackContainer.getAccessKey(),
                                        localStackContainer.getSecretKey())))
                .region(Region.of(localStackContainer.getRegion()))
                .build()
        createBucket(bucketName)
    }

    String getAccessKeyId() {
        localStackContainer.accessKey
    }

    String getSecretKeyId() {
        localStackContainer.secretKey
    }

    String getEndpoint() {
        "http://${localStackContainer.getNetworkAliases().get(0)}:${localStackContainer.getExposedPorts().get(0)}"
    }

    void createBucket(String bucketName) {
        if (!s3PbsService.(bucketName)) {
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3PbsService.createBucket(createBucketRequest);
        }
    }

    PutObjectResponse uploadAccount(String bucketName, AccountConfig account, String fileName = account.id) {
        uploadFile(bucketName, encode(account), "${DEFAULT_ACCOUNT_DIR}/${fileName}.json")
    }

    PutObjectResponse uploadStoredRequest(String bucketName, StoredRequest storedRequest, String fileName = storedRequest.requestId) {
        uploadFile(bucketName, encode(storedRequest.requestData), "${DEFAULT_REQUEST_DIR}/${fileName}.json")
    }

    PutObjectResponse uploadStoredResponse(String bucketName, StoredResponse storedRequest, String fileName = storedRequest.responseId) {
        uploadFile(bucketName, encode(storedRequest.storedAuctionResponse), "${DEFAULT_RESPONSE_DIR}/${fileName}.json")
    }

    PutObjectResponse uploadStoredImp(String bucketName, StoredImp storedImp, String fileName = storedImp.impId) {
        uploadFile(bucketName, encode(storedImp.impData), "${DEFAULT_IMPS_DIR}/${fileName}.json")
    }

    PutObjectResponse uploadFile(String bucketName, String fileBody, String path) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build()
        s3PbsService.putObject(putObjectRequest, RequestBody.fromString(fileBody))
    }
}
