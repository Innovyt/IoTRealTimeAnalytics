package com.innovyt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.innovyt" })
public class TransitIotWebserverApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransitIotWebserverApplication.class, args);
		System.out.println("enter into application");
	}
}
