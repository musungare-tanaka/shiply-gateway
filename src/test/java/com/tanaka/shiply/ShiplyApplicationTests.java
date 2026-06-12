package com.tanaka.shiply;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShiplyApplicationTests {

	private static final String FRONTEND_ORIGIN = "https://shiply-frontend.vercel.app";

	private static DisposableServer mockUserService;

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		ensureMockUserServiceStarted();
		registry.add("USER_SERVICE_URL", () -> "http://127.0.0.1:" + mockUserService.port());
	}

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@AfterAll
	static void tearDown() {
		if (mockUserService != null) {
			mockUserService.disposeNow();
			mockUserService = null;
		}
	}

	@Test
	void preflightRequestIsAllowedForFrontendOrigin() {
		EntityExchangeResult<byte[]> result = webTestClient.options()
				.uri("/auth/register")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody()
				.returnResult();

		assertThat(result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.containsExactly(FRONTEND_ORIGIN);
		assertThat(result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
				.containsExactly("true");
	}

	@Test
	void actualCorsResponseHeadersAreDeduplicated() {
		EntityExchangeResult<String> result = webTestClient.post()
				.uri("/auth/register")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{"email":"newtestuser@example.com","password":"TestPassword123"}
						""")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(String.class)
				.returnResult();

		HttpHeaders headers = result.getResponseHeaders();
		assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.containsExactly(FRONTEND_ORIGIN);
		assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
				.containsExactly("true");

		List<String> varyTokens = Arrays.stream(headers.getFirst(HttpHeaders.VARY).split(","))
				.map(String::trim)
				.filter(token -> !token.isEmpty())
				.toList();
		assertThat(varyTokens)
				.contains("Origin")
				.doesNotHaveDuplicates();
	}

	private static synchronized void ensureMockUserServiceStarted() {
		if (mockUserService != null) {
			return;
		}

		mockUserService = HttpServer.create()
				.host("127.0.0.1")
				.port(0)
				.route(routes -> routes
						.post("/auth/register", (request, response) -> duplicateCorsResponse(response))
						.post("/auth/login", (request, response) -> duplicateCorsResponse(response)))
				.bindNow();
	}

	private static Mono<Void> duplicateCorsResponse(reactor.netty.http.server.HttpServerResponse response) {
		return response
				.status(HttpResponseStatus.OK)
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN)
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN)
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
				.addHeader(HttpHeaders.VARY, "Origin")
				.addHeader(HttpHeaders.VARY, "Origin")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.sendString(Mono.just("{\"status\":\"ok\"}"))
				.then();
	}
}
