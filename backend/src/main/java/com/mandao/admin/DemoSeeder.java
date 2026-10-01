package com.mandao.admin;

import com.mandao.companies.Company;
import com.mandao.companies.CompanyRepository;
import com.mandao.companies.SavedAddress;
import com.mandao.companies.SavedAddressRepository;
import com.mandao.couriers.CourierDocument;
import com.mandao.couriers.CourierDocumentRepository;
import com.mandao.couriers.CourierEnums.DocumentType;
import com.mandao.couriers.CourierEnums.PayoutMethod;
import com.mandao.couriers.CourierEnums.VehicleType;
import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.evidence.StorageService;
import com.mandao.identity.Role;
import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.shared.MandaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.UUID;

/**
 * Datos de demostración en Valledupar (SEED_DEMO=true). NUNCA activar en producción:
 * crea cuentas con una contraseña conocida.
 */
@Component
@Order(2)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    public static final String DEMO_PASSWORD = "Mandao2026";
    /** PNG 1x1 usado como documento de muestra. */
    private static final byte[] SAMPLE_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final MandaoProperties props;
    private final UserRepository users;
    private final CourierProfileRepository couriers;
    private final CourierDocumentRepository documents;
    private final CompanyRepository companies;
    private final SavedAddressRepository addresses;
    private final StorageService storage;
    private final PasswordEncoder encoder;

    public DemoSeeder(MandaoProperties props, UserRepository users, CourierProfileRepository couriers,
                      CourierDocumentRepository documents, CompanyRepository companies,
                      SavedAddressRepository addresses, StorageService storage, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.couriers = couriers;
        this.documents = documents;
        this.companies = companies;
        this.addresses = addresses;
        this.storage = storage;
        this.encoder = encoder;
    }

    /** Los usuarios demo llegan con los términos vigentes aceptados (AC-001-12). */
    private static User accepted(User user) {
        user.acceptTerms(com.mandao.identity.AuthDtos.TERMS_VERSION);
        return user;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seedDemo() || users.existsByPhone("3000000000")) {
            return;
        }
        String hash = encoder.encode(DEMO_PASSWORD);
        users.save(new User("Operador Demo", "3000000000", "operador@mandao.demo", hash, Role.ADMIN, null, null));

        Object[][] courierData = {
                {"Carlos Mendoza", "3001000001", VehicleType.MOTO, "ABC12D", 10.4735, -73.2436},
                {"Luz Daza", "3001000002", VehicleType.MOTO, "XYZ34E", 10.4631, -73.2532},
                {"Andrés Oñate", "3001000003", VehicleType.BICICLETA, null, 10.4550, -73.2600},
                {"María Araújo", "3001000004", VehicleType.MOTO, "QWE56F", 10.4810, -73.2650},
                {"Jorge Zuleta", "3001000005", VehicleType.CARRO, "VAL789", 10.4480, -73.2400},
        };
        for (Object[] c : courierData) {
            User u = users.save(accepted(new User((String) c[0], (String) c[1], null, hash, Role.COURIER, null, "demo")));
            CourierProfile p = new CourierProfile(u.getId());
            VehicleType vehicle = (VehicleType) c[2];
            p.updateDetails(vehicle, (String) c[3], PayoutMethod.NEQUI, (String) c[1]);
            for (DocumentType type : DocumentType.requiredFor(vehicle)) {
                CourierDocument d = new CourierDocument(u.getId(), type);
                d.replaceFile(storage.put(SAMPLE_PNG, "image/png", "documents/" + u.getId(), "png"), "image/png");
                documents.save(d);
            }
            p.approve();
            p.updateLocation((Double) c[4], (Double) c[5]);
            couriers.save(p);
        }

        users.save(accepted(new User("Ana Castro", "3002000001", null, hash, Role.CLIENT, null, "instagram")));
        users.save(accepted(new User("Pedro Gnecco", "3002000002", null, hash, Role.CLIENT, null, "referido")));

        Company company = companies.save(new Company("Consultorio Jurídico del Cesar", "900123456-1",
                Company.Plan.EMPRESAS));
        users.save(accepted(new User("Laura Maestre", "3003000001", null, hash, Role.COMPANY_ADMIN, company.getId(), "b2b")));
        UUID companyId = company.getId();
        addresses.save(new SavedAddress(companyId, "Oficina", "Calle 16 # 9-45, Centro, Valledupar", 10.4768, -73.2466));
        addresses.save(new SavedAddress(companyId, "Juzgados", "Palacio de Justicia, Cra. 14, Valledupar", 10.4745,
                -73.2505));
        log.warn("Datos DEMO creados (contraseña {}). No usar en producción.", DEMO_PASSWORD);
    }
}
