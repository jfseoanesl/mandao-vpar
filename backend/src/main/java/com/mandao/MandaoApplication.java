package com.mandao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MandaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MandaoApplication.class, args);
    }
}
