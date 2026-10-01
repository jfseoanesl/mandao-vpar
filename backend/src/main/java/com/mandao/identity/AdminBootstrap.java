package com.mandao.identity;

import com.mandao.shared.MandaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el administrador inicial a partir de ADMIN_PHONE / ADMIN_PASSWORD si aún no existe. */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MandaoProperties.BootstrapAdmin config;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder, MandaoProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.config = props.bootstrapAdmin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (config == null || isBlank(config.phone()) || isBlank(config.password())) {
            return;
        }
        if (!config.phone().matches(AuthDtos.PHONE_REGEX) || !config.password().matches(AuthDtos.PASSWORD_REGEX)) {
            throw new IllegalStateException("ADMIN_PHONE o ADMIN_PASSWORD no cumplen el formato requerido");
        }
        if (users.existsByPhone(config.phone())) {
            return;
        }
        users.save(new User(config.name(), config.phone(), null, encoder.encode(config.password()), Role.ADMIN,
                null, null));
        log.info("Administrador inicial creado");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
