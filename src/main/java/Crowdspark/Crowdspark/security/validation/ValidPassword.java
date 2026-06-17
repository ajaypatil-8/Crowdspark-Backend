// src/main/java/Crowdspark/Crowdspark/security/validation/ValidPassword.java
// Feature #27 — Password Strength & Entropy Validation
//
// Drop this annotation on any password field in a DTO:
//   @ValidPassword
//   private String password;
//
// The validator (PasswordStrengthValidator) will:
//   1. Reject passwords on the common-passwords blacklist
//   2. Calculate Shannon entropy from character-set diversity + length
//   3. Apply penalties for sequential runs (abc, 123) and repeated chars (aaaa)
//   4. Reject anything scoring below FAIR (entropy < 36 bits)

package Crowdspark.Crowdspark.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordStrengthValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Password does not meet strength requirements";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
