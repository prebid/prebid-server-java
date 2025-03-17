package org.prebid.server.spring.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

public class SettingsConfigurationTest {

    @Test
    public void useStaticCredentialsShouldReturnTrueWhenStaticCredentialsAreUsed() {
        // given
        SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties s3Properties = new SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties();
        s3Properties.setAccessKeyId("testAccessKeyId");
        s3Properties.setSecretAccessKey("testSecretAccessKey");

        // when
        final boolean result = s3Properties.useStaticCredentials();

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void useStaticCredentialsShouldReturnFalseWhenStaticCredentialsAreNotUsed() {
        // given
        SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties s3Properties = new SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties();

        // when
        final boolean result = s3Properties.useStaticCredentials();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldCreateClientWhenStaticCredentialsNotUsed() throws URISyntaxException {
        // given
        SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties s3Properties = new SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties();
        s3Properties.setEndpoint("http://example.com");
        s3Properties.setForcePathStyle(true);
        s3Properties.setRegion("us-east-1");
        // Not setting access key and secret key properties should
        // cause the S3AsyncClient to use DefaultCredentialsProvider.

        // when
        S3AsyncClient client = new SettingsConfiguration.S3SettingsConfiguration().s3AsyncClient(s3Properties);

        // then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateClientWhenStaticCredentialsUsed() throws URISyntaxException {
        // given
        SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties s3Properties = new SettingsConfiguration.S3SettingsConfiguration.S3ConfigurationProperties();
        s3Properties.setEndpoint("http://localhost:4566");
        s3Properties.setForcePathStyle(true);
        s3Properties.setRegion("us-east-1");
        // Setting access key and secret key properties should cause
        // the S3AsyncClient to use StaticCredentialsProvider.
        s3Properties.setAccessKeyId("testAccessKeyId");
        s3Properties.setSecretAccessKey("testSecretAccessKey");

        // when
        S3AsyncClient client = new SettingsConfiguration.S3SettingsConfiguration().s3AsyncClient(s3Properties);

        // then
        assertThat(client).isNotNull();
    }

}
