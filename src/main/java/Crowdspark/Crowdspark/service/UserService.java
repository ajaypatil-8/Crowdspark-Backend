package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.RegisterRequest;
import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.User;

public interface UserService {

    UserResponse register(RegisterRequest request);

    User getById(Long id);

    User getByUsername(String username);







}
