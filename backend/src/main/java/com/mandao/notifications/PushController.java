package com.mandao.notifications;

import com.mandao.shared.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Spec 012. */
@RestController
public class PushController {

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    public record SubscriptionRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys) {
    }

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    /** AC-012-01. */
    @GetMapping("/api/push/key")
    public Map<String, String> key() {
        return Map.of("publicKey", push.publicKey());
    }

    /** AC-012-02. */
    @PostMapping("/api/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody SubscriptionRequest request) {
        push.subscribe(AuthUser.current().id(), request.endpoint(), request.keys().p256dh(), request.keys().auth());
    }

    /** AC-012-02. */
    @DeleteMapping("/api/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam("endpoint") String endpoint) {
        push.unsubscribe(AuthUser.current().id(), endpoint);
    }
}
