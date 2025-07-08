package com.chae.promo.auth.service;

public interface AuthService {

    String createAnonymousToken();
    String getAnonIdFromToken(String token);
}
