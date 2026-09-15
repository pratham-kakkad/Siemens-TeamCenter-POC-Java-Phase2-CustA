package fr.formcraft.extensions.changerequest;

import fr.formcraft.common.exception.FormCraftException;
import fr.formcraft.model.enums.ChangeRequestStatus;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionContext;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionHandler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Nordic Snacks Co. policy: a change request needs at least a 4-hour cooling-off
 * period between being requested and being approved, so a reviewer can't rubber-stamp
 * a request the moment it lands. Plugged into Customization Gate 1.
 */
@Component
public class EnforceMinimumReviewWindowHandler implements ChangeRequestTransitionHandler {

    private static final Duration MINIMUM_REVIEW_WINDOW = Duration.ofHours(4);

    @Override
    public void beforeTransition(ChangeRequestTransitionContext context) {
        if (context.toStatus() != ChangeRequestStatus.APPROVED) {
            return;
        }

        LocalDateTime requestedAt = context.changeRequest().getRequestedAt();
        if (requestedAt == null) {
            return;
        }

        Duration elapsed = Duration.between(requestedAt, LocalDateTime.now());
        if (elapsed.compareTo(MINIMUM_REVIEW_WINDOW) < 0) {
            Duration remaining = MINIMUM_REVIEW_WINDOW.minus(elapsed);
            throw new FormCraftException(
                    "Change requests need at least a " + MINIMUM_REVIEW_WINDOW.toHours()
                            + "-hour review window before approval ("
                            + Math.max(remaining.toMinutes(), 1) + " minute(s) remaining)");
        }
    }
}
