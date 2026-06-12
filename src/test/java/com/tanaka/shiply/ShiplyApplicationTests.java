package com.tanaka.shiply;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "USER_SERVICE_URL=http://localhost:65535"
)
class ShiplyApplicationTests {

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void preflightRequestIsAllowedForFrontendOrigin() {
		webTestClient.options()
				.uri("/auth/register")
				.header("Origin", "https://shiply-frontend.vercel.app")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,authorization")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectHeader().valueEquals("Access-Control-Allow-Origin", "https://shiply-frontend.vercel.app")
				.expectHeader().exists("Access-Control-Allow-Methods")
				.expectHeader().exists("Access-Control-Allow-Headers");
	}

}
