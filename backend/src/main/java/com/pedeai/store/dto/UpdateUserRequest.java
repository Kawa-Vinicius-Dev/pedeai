package com.pedeai.store.dto;

import com.pedeai.shared.security.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Atualização parcial: campo ausente (null) fica como está. */
public record UpdateUserRequest(
        @Pattern(regexp = ".*\\S.*", message = "O nome não pode ficar em branco.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String name,

        Role role,

        Boolean active,

        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String password
) {
}
