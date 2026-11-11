package com.menta.bff.application.dto;

/**
 * Data of a staff account an administrator creates, already trimmed and validated by the use case.
 *
 * <p>SECURITY: never log the email nor the names: {@link #toString()} leaves them out.
 * </p>
 *
 * @param email     address that receives the activation link
 * @param firstName given name
 * @param lastName  family name
 * @param role      staff role of the account
 */
public record CreateUserCommand(String email, String firstName, String lastName, UserRole role) {

    /**
     * Custom toString that keeps the personal data out of logs.
     *
     * @return the role only
     */
    @Override
    public String toString() {
        return "CreateUserCommand[email=***, firstName=***, lastName=***, role=" + role + "]";
    }
}
