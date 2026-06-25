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
	private static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:3000";

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
	void preflightRequestIsAllowedForLocalFrontendOrigin() {
		EntityExchangeResult<byte[]> result = webTestClient.options()
				.uri("/auth/login")
				.header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody()
				.returnResult();

		assertThat(result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.containsExactly(LOCAL_FRONTEND_ORIGIN);
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

	@Test
	void v1ProjectManagementRoutesAreForwardedToUserService() {
		webTestClient.get()
				.uri("/api/v1/projects")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.route").isEqualTo("v1-projects");
	}

	private static synchronized void ensureMockUserServiceStarted() {
		if (mockUserService != null) {
			return;
		}

			mockUserService = HttpServer.create()
					.host("127.0.0.1")
					.port(0)
					.route(routes -> routes
							.post("/auth/register", (request, response) -> duplicateCorsResponse(request.requestHeaders().get(HttpHeaders.ORIGIN), response))
							.post("/auth/login", (request, response) -> duplicateCorsResponse(request.requestHeaders().get(HttpHeaders.ORIGIN), response))
							.get("/api/v1/projects", (request, response) -> response
									.status(HttpResponseStatus.OK)
									.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
									.sendString(Mono.just("{\"route\":\"v1-projects\"}"))
									.then()))
					.bindNow();
	}

	private static Mono<Void> duplicateCorsResponse(String origin, reactor.netty.http.server.HttpServerResponse response) {
		String responseOrigin = origin == null || origin.isBlank() ? FRONTEND_ORIGIN : origin;

		return response
				.status(HttpResponseStatus.OK)
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, responseOrigin)
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, responseOrigin)
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
				.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
				.addHeader(HttpHeaders.VARY, "Origin")
				.addHeader(HttpHeaders.VARY, "Origin")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.sendString(Mono.just("{\"status\":\"ok\"}"))
				.then();
	}
}
