package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String phoneNumber;

    private Set<Role> roles;

    private LocalDateTime createdAt;
}
