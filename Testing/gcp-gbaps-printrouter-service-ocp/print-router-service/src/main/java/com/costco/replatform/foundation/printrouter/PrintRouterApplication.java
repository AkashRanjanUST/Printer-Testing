package com.costco.replatform.foundation.printrouter;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.costco.replatform.foundation.printrouter.config.PrinterIppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(PrinterIppProperties.class)
@EnableScheduling
public class PrintRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                PrintRouterApplication.class,
                args
        );
    }
}