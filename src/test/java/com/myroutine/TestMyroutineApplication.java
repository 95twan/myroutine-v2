package com.myroutine;

import org.springframework.boot.SpringApplication;

public class TestMyroutineApplication {

	public static void main(String[] args) {
		SpringApplication.from(MyroutineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
