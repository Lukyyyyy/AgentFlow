package com.agentflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.agentflow.mapper")
public class AgentFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentFlowApplication.class, args);
	}

}
