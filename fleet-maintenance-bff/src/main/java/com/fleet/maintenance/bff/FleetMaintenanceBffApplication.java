package com.fleet.maintenance.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.fleet.maintenance")
@EnableScheduling
public class FleetMaintenanceBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetMaintenanceBffApplication.class, args);
    }
}
