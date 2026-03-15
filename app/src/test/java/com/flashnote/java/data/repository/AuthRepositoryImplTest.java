package com.flashnote.java.data.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.model.User;
import com.flashnote.java.data.remote.AuthService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Unit tests for AuthRepositoryImpl.
 * Tests login, register, and logout functionality.
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthRepositoryImplTest {

    @Mock
    private AuthService authService;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private Call<ApiResponse<LoginResponse>> loginCall;

    @Mock
    private Call<ApiResponse<Void>> registerCall;

    @Mock
    private Call<ApiResponse<Void>> logoutCall;

    private AuthRepositoryImpl authRepository;

    @Before
    public void setUp() {
        authRepository = new AuthRepositoryImpl(authService, tokenManager);
    }

    /**
     * Test: login_success_callsApiAndSavesTokens
     * Verifies that successful login calls the API and saves tokens.
     */
    @Test
    public void login_success_callsApiAndSavesTokens() {
        // Arrange
        String username = "testuser";
        String password = "password123";
        
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken("access_token_123");
        loginResponse.setRefreshToken("refresh_token_456");
        loginResponse.setExpiresIn(3600L);
        loginResponse.setUser(user);

        ApiResponse<LoginResponse> apiResponse = new ApiResponse<>(200, "Login successful", loginResponse);

        when(authService.login(any())).thenReturn(loginCall);
        
        // Capture the callback to simulate async response
        ArgumentCaptor<Callback<ApiResponse<LoginResponse>>> loginCallbackCaptor = 
                ArgumentCaptor.forClass(Callback.class);

        // Act
        authRepository.login(username, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(LoginResponse response) {
                // Verify response
                assert response.getToken().equals("access_token_123");
                assert response.getUser() != null;
                assert response.getUser().getUsername().equals("testuser");
            }

            @Override
            public void onError(String message, int code) {
                throw new AssertionError("Should not reach here: " + message);
            }
        });

        // Simulate successful response from API
        verify(authService.login(any())).enqueue(loginCallbackCaptor.capture());
        loginCallbackCaptor.getValue().onResponse(loginCall, Response.success(apiResponse));

        // Assert - Verify token manager was called to save tokens
        verify(tokenManager).saveTokens("access_token_123", "refresh_token_456", 3600L);
        verify(tokenManager).saveUserId(1L);
        verify(tokenManager).saveUsername("testuser");
    }

    /**
     * Test: login_failure_returnsError
     * Verifies that login failure returns error properly.
     */
    @Test
    public void login_failure_returnsError() {
        // Arrange
        String username = "testuser";
        String password = "wrongpassword";

        ApiResponse<LoginResponse> apiResponse = new ApiResponse<>(401, "Invalid credentials", null);

        when(authService.login(any())).thenReturn(loginCall);
        
        ArgumentCaptor<Callback<ApiResponse<LoginResponse>>> loginCallbackCaptor = 
                ArgumentCaptor.forClass(Callback.class);

        // Act
        authRepository.login(username, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(LoginResponse response) {
                throw new AssertionError("Should not reach here");
            }

            @Override
            public void onError(String message, int code) {
                // Verify error is returned
                assert message.contains("Invalid credentials");
                assert code == 401;
            }
        });

        // Simulate error response from API
        verify(authService.login(any())).enqueue(loginCallbackCaptor.capture());
        loginCallbackCaptor.getValue().onResponse(loginCall, Response.success(apiResponse));

        // Assert - Verify token manager was NOT called
        verify(tokenManager, never()).saveTokens(anyString(), anyString(), anyLong());
    }

    /**
     * Test: login_failure_networkError
     * Verifies that network failure returns error properly.
     */
    @Test
    public void login_failure_networkError() {
        // Arrange
        String username = "testuser";
        String password = "password123";

        when(authService.login(any())).thenReturn(loginCall);
        
        ArgumentCaptor<Callback<ApiResponse<LoginResponse>>> loginCallbackCaptor = 
                ArgumentCaptor.forClass(Callback.class);

        // Act
        authRepository.login(username, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(LoginResponse response) {
                throw new AssertionError("Should not reach here");
            }

            @Override
            public void onError(String message, int code) {
                // Verify network error is returned
                assert message.contains("Network error");
                assert code == -1;
            }
        });

        // Simulate network failure
        verify(authService.login(any())).enqueue(loginCallbackCaptor.capture());
        loginCallbackCaptor.getValue().onFailure(loginCall, new RuntimeException("Connection timeout"));
    }

    /**
     * Test: register_success_callsApi
     * Verifies that register calls the API successfully.
     */
    @Test
    public void register_success_callsApi() {
        // Arrange
        String username = "newuser";
        String email = "newuser@example.com";
        String password = "password123";

        ApiResponse<Void> apiResponse = new ApiResponse<>(201, "Registration successful", null);

        when(authService.register(any())).thenReturn(registerCall);
        
        ArgumentCaptor<Callback<ApiResponse<Void>>> registerCallbackCaptor = 
                ArgumentCaptor.forClass(Callback.class);

        // Act
        authRepository.register(username, email, password, new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                // Verify success is called
            }

            @Override
            public void onError(String message, int code) {
                throw new AssertionError("Should not reach here: " + message);
            }
        });

        // Simulate successful response from API
        verify(authService.register(any())).enqueue(registerCallbackCaptor.capture());
        registerCallbackCaptor.getValue().onResponse(registerCall, Response.success(apiResponse));
    }

    /**
     * Test: register_failure_returnsError
     * Verifies that registration failure returns error properly.
     */
    @Test
    public void register_failure_returnsError() {
        // Arrange
        String username = "existinguser";
        String email = "existing@example.com";
        String password = "password123";

        ApiResponse<Void> apiResponse = new ApiResponse<>(409, "User already exists", null);

        when(authService.register(any())).thenReturn(registerCall);
        
        ArgumentCaptor<Callback<ApiResponse<Void>>> registerCallbackCaptor = 
                ArgumentCaptor.forClass(Callback.class);

        // Act
        authRepository.register(username, email, password, new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                throw new AssertionError("Should not reach here");
            }

            @Override
            public void onError(String message, int code) {
                // Verify error is returned
                assert message.contains("User already exists");
                assert code == 409;
            }
        });

        // Simulate error response from API
        verify(authService.register(any())).enqueue(registerCallbackCaptor.capture());
        registerCallbackCaptor.getValue().onResponse(registerCall, Response.success(apiResponse));
    }

    /**
     * Test: logout_clearsTokens
     * Verifies that logout clears tokens and calls API.
     */
    @Test
    public void logout_clearsTokens() {
        // Arrange
        when(authService.logout()).thenReturn(logoutCall);
        
        ArgumentCaptor<Callback<ApiResponse<Void>>> logoutCallbackCaptor = 
                ArgumentCaptor.forClass(Callback.class);

        // Act
        authRepository.logout();

        // Assert - Verify logout API is called
        verify(authService.logout()).enqueue(logoutCallbackCaptor.capture());
        
        // Verify token manager clear is called immediately (synchronous)
        verify(tokenManager).clearTokens();
        
        // Simulate async response (should not affect outcome)
        logoutCallbackCaptor.getValue().onResponse(logoutCall, Response.success(new ApiResponse<>(200, "OK", null)));
    }

    /**
     * Test: isLoggedIn_returnsTokenManagerState
     * Verifies isLoggedIn delegates to TokenManager.
     */
    @Test
    public void isLoggedIn_returnsTokenManagerState() {
        // Arrange
        when(tokenManager.hasToken()).thenReturn(true);

        // Act
        boolean result = authRepository.isLoggedIn();

        // Assert
        assert result == true;
        verify(tokenManager).hasToken();
    }

    /**
     * Test: isLoggedIn_returnsFalseWhenNoToken
     * Verifies isLoggedIn returns false when no token exists.
     */
    @Test
    public void isLoggedIn_returnsFalseWhenNoToken() {
        // Arrange
        when(tokenManager.hasToken()).thenReturn(false);

        // Act
        boolean result = authRepository.isLoggedIn();

        // Assert
        assert result == false;
        verify(tokenManager).hasToken();
    }
}
