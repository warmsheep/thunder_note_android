package com.flashnote.java.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import retrofit2.http.Body;
import retrofit2.http.Query;

public class AuthServiceContractTest {

    @Test
    public void refreshToken_usesRequestBodyInsteadOfQueryParameter() throws NoSuchMethodException {
        Method method = AuthService.class.getMethod("refreshToken", RefreshTokenRequest.class);
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        assertEquals(1, parameterAnnotations.length);
        assertTrue(parameterAnnotations[0].length > 0);

        Body body = null;
        for (Annotation annotation : parameterAnnotations[0]) {
            if (annotation instanceof Body found) {
                body = found;
            }
            assertTrue("refreshToken should not use @Query", !(annotation instanceof Query));
        }

        assertNotNull("refreshToken should declare @Body", body);
    }
}
