package org.example.coin_laundry_app_backend.config.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JWTAuthenticationFilterTest {

    @InjectMocks
    private JWTAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private JWTHelper jwtHelper;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;


    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 토큰_인증_성공() throws Exception {
        // Arrange
        var expectedUserId = 1L;
        when(request.getHeader(any())).thenReturn("Bearer validToken");
        when(jwtHelper.verify(anyString())).thenReturn(expectedUserId);
        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        // Assert
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(
            expectedUserId);
    }

    @MethodSource("invalidTokenProvider")
    @ParameterizedTest
    void 토큰_인증_실패_잘못된_토큰(String token) throws Exception {
        // Arrange
        when(request.getHeader(any())).thenReturn(token);
        lenient().when(jwtHelper.verify(anyString())).thenThrow(JWTVerificationException.class);
        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        // Assert
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 토큰_인증_실패_토큰_없음() throws Exception {
        // Arrange
        when(request.getHeader(any())).thenReturn(null);
        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        // Assert
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static Stream<Arguments> invalidTokenProvider() {
        return Stream.of(
            Arguments.of("invalidToken"),
            Arguments.of("Bearer"),
            Arguments.of("Bearer "),
            Arguments.of("Bearer invalidToken")
        );
    }
}