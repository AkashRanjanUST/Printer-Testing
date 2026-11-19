package com.costco.replatform.foundation.printrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PrinterIppPropertiesTest {

    @Nested
    @DisplayName("Plain Java behaviour")
    class PlainJava {

        @Test
        @DisplayName("Defaults: timeouts are 10s / 30s, all strings are null")
        void defaults() {
            PrinterIppProperties properties = new PrinterIppProperties();

            assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(10);
            assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(30);
            assertThat(properties.getQueueUrl()).isNull();
            assertThat(properties.getRequestingUser()).isNull();
            assertThat(properties.getDomainUsername()).isNull();
            assertThat(properties.getPassword()).isNull();
            assertThat(properties.getJobName()).isNull();
            assertThat(properties.getPrintContent()).isNull();
        }

        @Test
        @DisplayName("Setters and getters round-trip every field")
        void settersAndGetters_roundTrip() {
            PrinterIppProperties properties = new PrinterIppProperties();

            properties.setQueueUrl("http://print-server.test.invalid:631/printers/TestPrinter");
            properties.setRequestingUser("test-user");
            properties.setDomainUsername("TEST\\test-user");
            properties.setPassword("test-password");
            properties.setJobName("TEST_JOB_");
            properties.setPrintContent("content");
            properties.setConnectTimeoutSeconds(5);
            properties.setRequestTimeoutSeconds(15);

            assertThat(properties.getQueueUrl())
                    .isEqualTo("http://print-server.test.invalid:631/printers/TestPrinter");
            assertThat(properties.getRequestingUser()).isEqualTo("test-user");
            assertThat(properties.getDomainUsername()).isEqualTo("TEST\\test-user");
            assertThat(properties.getPassword()).isEqualTo("test-password");
            assertThat(properties.getJobName()).isEqualTo("TEST_JOB_");
            assertThat(properties.getPrintContent()).isEqualTo("content");
            assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(5);
            assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Spring property binding")
    class Binding {

        @Configuration(proxyBeanMethods = false)
        @EnableConfigurationProperties(PrinterIppProperties.class)
        static class PropertiesConfig {
        }

        private final ApplicationContextRunner contextRunner =
                new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

        @Test
        @DisplayName("All printer.ipp.* properties bind to their fields (kebab-case names)")
        void allProperties_bind() {
            contextRunner
                    .withPropertyValues(
                            "printer.ipp.queue-url=http://print-server.test.invalid:631/printers/TestPrinter",
                            "printer.ipp.requesting-user=test-user",
                            "printer.ipp.domain-username=TEST\\test-user",
                            "printer.ipp.password=test-password",
                            "printer.ipp.job-name=TEST_JOB_",
                            "printer.ipp.print-content=content",
                            "printer.ipp.connect-timeout-seconds=3",
                            "printer.ipp.request-timeout-seconds=7")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        PrinterIppProperties properties = context.getBean(PrinterIppProperties.class);

                        assertThat(properties.getQueueUrl())
                                .isEqualTo("http://print-server.test.invalid:631/printers/TestPrinter");
                        assertThat(properties.getRequestingUser()).isEqualTo("test-user");
                        assertThat(properties.getDomainUsername()).isEqualTo("TEST\\test-user");
                        assertThat(properties.getPassword()).isEqualTo("test-password");
                        assertThat(properties.getJobName()).isEqualTo("TEST_JOB_");
                        assertThat(properties.getPrintContent()).isEqualTo("content");
                        assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(3);
                        assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(7);
                    });
        }

        @Test
        @DisplayName("Timeouts keep their defaults when not configured")
        void missingTimeouts_keepDefaults() {
            contextRunner
                    .withPropertyValues("printer.ipp.queue-url=http://print-server.test.invalid/printers/X")
                    .run(context -> {
                        PrinterIppProperties properties = context.getBean(PrinterIppProperties.class);

                        assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(10);
                        assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(30);
                    });
        }

        @Test
        @DisplayName("A non-numeric timeout makes the application fail to start")
        void nonNumericTimeout_failsStartup() {
            contextRunner
                    .withPropertyValues("printer.ipp.connect-timeout-seconds=ten")
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}