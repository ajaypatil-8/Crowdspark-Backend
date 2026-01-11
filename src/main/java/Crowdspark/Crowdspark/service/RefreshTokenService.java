package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.RefreshToken;

public interface RefreshTokenService {


    RefreshToken create(Long userId);


    RefreshToken validate(String token);


    void revoke(String token);


    void revokeAll(Long userId);
}
