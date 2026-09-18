package com.example.m321processingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class M321ProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(M321ProcessingServiceApplication.class, args);
    }

}
