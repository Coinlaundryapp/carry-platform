package com.carry_laundry.carry_backend.config.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.auth0.jwt.exceptions.JWTVerificationException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWTTokenParseFilter는")
class JWTTokenParseFilterTest {

    @InjectMocks
    private JWTTokenParseFilter jwtTokenParseFilter;
    @Mock
    private JWTHelper jwtHelper;
    @Mock
    private WebFilterChain webFilterChain;

    @BeforeEach
    void init() {
        given(webFilterChain.filter(any())).willReturn(Mono.empty());
    }

    @Nested
    @DisplayName("토큰 검증을 할 때")
    class whenVerifyToken {

        @DisplayName("토큰이 없으면 JWTVerificationException을 전달한다.")
        @Test
        void ifTokenIsNullShouldReturnException() {
            // Arrange
            MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            // Act & Assert
            StepVerifier
                .create(jwtTokenParseFilter.filter(exchange, webFilterChain))
                .verifyComplete();
            assertThat(exchange.getAttributes().get("exception"))
                .isInstanceOf(JWTVerificationException.class);
        }

        @DisplayName("토큰이 형식이 올바르지 않다면 JWTVerificationException을 전달한다.")
        @ParameterizedTest
        @MethodSource("invalidTokenProvider")
        void ifTokenIsInvalidShouldReturnException(String token) {
            // Arrange
            MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("Authorization", token)
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            given(jwtHelper.parse(any())).willThrow(JWTVerificationException.class);
            // Act & Assert
            StepVerifier
                .create(jwtTokenParseFilter.filter(exchange, webFilterChain))
                .verifyComplete();
            assertThat(exchange.getAttributes().get("exception"))
                .isInstanceOf(JWTVerificationException.class);
        }

        @DisplayName("토큰 형식이 올바르지 않다면 JWTVerificationException을 전달한다.")
        @ParameterizedTest
        @MethodSource("wrongTokenProvider")
        void ifTokenIsWrongShouldReturnException(String token) {
            // Arrange
            MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("Authorization", token)
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            // Act & Assert
            StepVerifier
                .create(jwtTokenParseFilter.filter(exchange, webFilterChain))
                .verifyComplete();
            assertThat(exchange.getAttributes().get("exception"))
                .isInstanceOf(JWTVerificationException.class);
        }

        @DisplayName("토큰이 올바르다면 JWTAuthenticationToken을 생성한다.")
        @Test
        void ifTokenIsValidShouldReturnJWTAuthenticationToken() {
            // Arrange
            MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("Authorization", "Bearer validToken")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            given(jwtHelper.parse(any())).willReturn(new TokenDetail(1L, Map.of()));
            // Act & Assert
            StepVerifier.create(jwtTokenParseFilter.filter(exchange, webFilterChain))
                .verifyComplete();
        }

        private static Stream<Arguments> invalidTokenProvider() {
            return Stream.of(
                Arguments.of("Bearer "),
                Arguments.of("Bearer invalidToken")
            );
        }

        private static Stream<Arguments> wrongTokenProvider() {
            return Stream.of(
                Arguments.of("invalidToken"),
                Arguments.of("Bearer")
            );
        }

    }


}