package com.mandao.notifications;

/**
 * Genera el par de llaves VAPID una sola vez (spec 012):
 * {@code docker compose run --rm --no-deps --entrypoint java backend -Dloader.main=com.mandao.notifications.VapidKeyGenerator
 * -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher}
 */
public final class VapidKeyGenerator {

    private VapidKeyGenerator() {
    }

    public static void main(String[] args) {
        WebPushCrypto.Vapid vapid = WebPushCrypto.vapid(WebPushCrypto.generateKeyPair());
        System.out.println("VAPID_PUBLIC_KEY=" + vapid.publicKeyBase64());
        System.out.println("VAPID_PRIVATE_KEY=" + WebPushCrypto.privateKeyBase64(vapid.privateKey()));
    }
}
