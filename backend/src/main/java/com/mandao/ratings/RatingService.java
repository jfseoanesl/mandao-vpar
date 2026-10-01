package com.mandao.ratings;

import com.mandao.analytics.AnalyticsService;
import com.mandao.orders.Errand;
import com.mandao.orders.ErrandAccess;
import com.mandao.orders.ErrandLifecycle;
import com.mandao.orders.ErrandStatus;
import com.mandao.orders.ErrandDtos.RatingRequest;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

/** AC-008-01 / AC-008-02. */
@Service
public class RatingService {

    private final RatingRepository ratings;
    private final ErrandAccess access;
    private final ErrandLifecycle lifecycle;
    private final AnalyticsService analytics;

    public RatingService(RatingRepository ratings, ErrandAccess access, ErrandLifecycle lifecycle,
                         AnalyticsService analytics) {
        this.ratings = ratings;
        this.access = access;
        this.lifecycle = lifecycle;
        this.analytics = analytics;
    }

    @Transactional
    public Errand rate(UUID errandId, RatingRequest req, AuthUser user) {
        Errand errand = access.requireOwner(errandId, user);
        if (ratings.existsByErrandId(errandId) || errand.getStatus() == ErrandStatus.RATED) {
            throw ApiException.conflict("Ya calificaste este mandado");
        }
        if (errand.getStatus() != ErrandStatus.COMPLETED) {
            throw ApiException.conflict("Solo puedes calificar mandados completados");
        }
        String tags = req.tags() == null || req.tags().isEmpty() ? null
                : req.tags().stream().distinct().map(Enum::name).collect(Collectors.joining(","));
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().trim();
        ratings.save(new Rating(errandId, user.id(), errand.getCourierId(), req.stars(), tags, comment));
        lifecycle.transition(errand, ErrandStatus.RATED, user.id(), req.stars() + " estrellas");
        analytics.track("rating_submitted", user.id(), errandId, String.valueOf(req.stars()), null);
        return errand;
    }
}
