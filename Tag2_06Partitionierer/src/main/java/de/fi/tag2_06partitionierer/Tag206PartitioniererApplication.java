package de.fi.tag2_06partitionierer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Einstiegspunkt der Spring-Boot-Anwendung.
// @SpringBootApplication aktiviert auto-configuration, component scan und @Configuration.
// spring.batch.job.enabled=false in application.properties verhindert, dass Spring Batch
// den Job automatisch beim Start ausführt — der Start erfolgt manuell über PartitionJobRunner.
@SpringBootApplication
public class Tag206PartitioniererApplication {

    public static void main(String[] args) {
        SpringApplication.run(Tag206PartitioniererApplication.class, args);
    }

}
