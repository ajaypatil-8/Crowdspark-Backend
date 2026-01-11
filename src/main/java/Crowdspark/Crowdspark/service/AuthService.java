package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.User;

public interface AuthService {

    User login(String identifier, String password);
}
