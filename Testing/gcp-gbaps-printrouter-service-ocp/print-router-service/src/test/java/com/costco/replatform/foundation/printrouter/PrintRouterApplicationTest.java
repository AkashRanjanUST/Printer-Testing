package com.costco.replatform.foundation.printrouter;

import com.costco.replatform.foundation.printrouter.config.PrinterIppProperties;
import com.costco.replatform.foundation.printrouter.controller.PrintRouterController;
import com.costco.replatform.foundation.printrouter.exception.GlobalExceptionHandler;
import com.costco.replatform.foundation.printrouter.service.ActiveDirectoryDiscoveryService;
import com.costco.replatform.foundation.printrouter.service.PrintServerDiscoveryService;
import com.costco.replatform.foundation.printrouter.service.PrinterConnectivityService;
import com.costco.replatform.foundation.printrouter.util.ResponseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest
@ActiveProfiles("test")
class PrintRouterApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Application context starts with the test profile and all beans wired")
    void contextLoads_withAllBeans() {
        assertThat(context.getBean(PrintRouterController.class)).isNotNull();
        assertThat(context.getBean(ActiveDirectoryDiscoveryService.class)).isNotNull();
        assertThat(context.getBean(PrinterConnectivityService.class)).isNotNull();
        assertThat(context.getBean(PrintServerDiscoveryService.class)).isNotNull();
        assertThat(context.getBean(ResponseHandler.class)).isNotNull();
        assertThat(context.getBean(GlobalExceptionHandler.class)).isNotNull();
    }

    @Test
    @DisplayName("Only the 'test' profile is active - the hardcoded local-vdi profile is NOT loaded")
    void onlyTestProfileIsActive() {
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("test");
    }

    @Test
    @DisplayName("printer.ipp.* values come from application-test.properties")
    void printerIppProperties_boundFromTestProfile() {
        PrinterIppProperties properties = context.getBean(PrinterIppProperties.class);

        assertThat(properties.getQueueUrl())
                .isEqualTo("http://print-server.test.invalid:631/printers/TestPrinter");
        assertThat(properties.getRequestingUser()).isEqualTo("test-user");
        assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("main() delegates to SpringApplication.run with the given arguments")
    void main_startsSpringApplication() {
        String[] args = {"--spring.main.banner-mode=off"};

        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            PrintRouterApplication.main(args);

            spring.verify(() -> SpringApplication.run(PrintRouterApplication.class, args));
        }
    }
}