package com.chae.promo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PromoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PromoApplication.class, args);
	}

}
