package com.enterprise.erm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // required for ReviewScheduler (@Scheduled); it was never switched on before
public class ErmApplication {
    public static void main(String[] args) { SpringApplication.run(ErmApplication.class, args); }
}
