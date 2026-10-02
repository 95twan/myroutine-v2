package com.myroutine;

import org.springframework.boot.SpringApplication;

public class TestMyRoutineApplication {

	public static void main(String[] args) {
		SpringApplication.from(MyRoutineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
