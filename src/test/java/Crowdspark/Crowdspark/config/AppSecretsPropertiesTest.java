package Crowdspark.Crowdspark.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;


class AppSecretsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(AppSecretsProperties.class)
    static class TestConfig {
    }

    private static final String[] ALL_SECRETS_PRESENT = {
            "app.secrets.db-password=test-db-password",
            "app.secrets.jwt-secret=12345678901234567890123456789012", // 32 chars — meets @Size(min = 32)
            "app.secrets.cloudinary-api-key=test-cloudinary-key",
            "app.secrets.cloudinary-api-secret=test-cloudinary-secret",
            "app.secrets.mail-password=test-brevo-smtp-key",
            "app.secrets.google-client-secret=test-google-secret",
            "app.secrets.github-client-secret=test-github-secret",
            "app.secrets.razorpay-key-secret=test-razorpay-secret",
    };

    @Test
    void startsSuccessfully_whenAllRequiredSecretsArePresent() {
        contextRunner
                .withPropertyValues(ALL_SECRETS_PRESENT)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsToStart_whenMailPasswordIsMissing() {
        String[] withoutMailPassword = withoutProperty(ALL_SECRETS_PRESENT, "app.secrets.mail-password");

        contextRunner
                .withPropertyValues(withoutMailPassword)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStart_whenMailPasswordIsBlank() {
        String[] blankMailPassword = withoutProperty(ALL_SECRETS_PRESENT, "app.secrets.mail-password");
        String[] withBlank = append(blankMailPassword, "app.secrets.mail-password=");

        contextRunner
                .withPropertyValues(withBlank)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStart_whenJwtSecretIsShorterThan32Chars() {
        String[] withoutJwt = withoutProperty(ALL_SECRETS_PRESENT, "app.secrets.jwt-secret");
        String[] withShortJwt = append(withoutJwt, "app.secrets.jwt-secret=tooShort");

        contextRunner
                .withPropertyValues(withShortJwt)
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] withoutProperty(String[] properties, String keyPrefix) {
        return java.util.Arrays.stream(properties)
                .filter(p -> !p.startsWith(keyPrefix + "="))
                .toArray(String[]::new);
    }

    private static String[] append(String[] properties, String extra) {
        String[] result = java.util.Arrays.copyOf(properties, properties.length + 1);
        result[properties.length] = extra;
        return result;
    }
}