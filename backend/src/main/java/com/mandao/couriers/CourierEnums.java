package com.mandao.couriers;

import java.util.EnumSet;
import java.util.Set;

public final class CourierEnums {

    private CourierEnums() {
    }

    public enum CourierStatus { PENDING, APPROVED, REJECTED }

    public enum VehicleType {
        A_PIE, BICICLETA, MOTO, CARRO;

        public boolean motorized() {
            return this == MOTO || this == CARRO;
        }
    }

    public enum PayoutMethod { NEQUI, DAVIPLATA, BANCO }

    public enum DocumentType {
        CEDULA_FRENTE, CEDULA_REVERSO, SELFIE, SOAT, LICENCIA, TARJETA_PROPIEDAD;

        public static Set<DocumentType> requiredFor(VehicleType vehicle) {
            Set<DocumentType> required = EnumSet.of(CEDULA_FRENTE, CEDULA_REVERSO, SELFIE);
            if (vehicle != null && vehicle.motorized()) {
                required.addAll(EnumSet.of(SOAT, LICENCIA, TARJETA_PROPIEDAD));
            }
            return required;
        }
    }
}
