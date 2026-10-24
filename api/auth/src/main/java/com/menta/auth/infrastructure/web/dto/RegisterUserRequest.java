package com.menta.auth.infrastructure.web.dto;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserProfile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request DTO for user registration.
 * Infrastructure layer - web request object.
 *
 * <p>{@code phone} is expected already normalized to E.164 (the web client strips spaces, dashes and parentheses)
 * and is validated strictly. {@code birthDate} is the only optional field.
 */
public record RegisterUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    String password,

    @NotNull(message = "Role is required")
    Role role,

    @NotBlank(message = "First name is required")
    @Size(max = UserProfile.NAME_MAX_LENGTH, message = "First name must not exceed 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(max = UserProfile.NAME_MAX_LENGTH, message = "Last name must not exceed 100 characters")
    String lastName,

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = UserProfile.PHONE_REGEX, message = "Phone must be in E.164 format")
    String phone,

    @Past(message = "Birth date must be in the past")
    LocalDate birthDate
) {
}
