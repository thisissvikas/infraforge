package io.infraforge;

import io.infraforge.config.InfraforgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InfraforgeProperties.class)
public class InfraforgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfraforgeApplication.class, args);
    }
}
