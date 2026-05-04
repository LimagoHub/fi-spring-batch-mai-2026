package de.fi.second;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication kombiniert @Configuration, @EnableAutoConfiguration und
// @ComponentScan — Spring Boot erkennt alle Beans im Paket und seinen Unterpaketen
// automatisch, ohne XML-Konfiguration.
@SpringBootApplication
public class SecondApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecondApplication.class, args);
	}

}
