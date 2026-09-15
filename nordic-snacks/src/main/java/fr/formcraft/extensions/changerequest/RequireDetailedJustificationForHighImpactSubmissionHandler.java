package fr.formcraft.extensions.changerequest;

import fr.formcraft.common.exception.FormCraftException;
import fr.formcraft.model.entity.ChangeRequest;
import fr.formcraft.model.enums.ChangeRequestStatus;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionContext;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionHandler;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Nordic Snacks Co. policy: a change request flagged as high-impact — either by its
 * own impact notes or because it touches an already-allergen-flagged finished
 * product — needs a properly detailed reason before it can even be submitted for
 * review, not just a one-liner. Plugged into Customization Gate 1; vetoes the
 * SUBMITTED transition (the first hop of {@code ChangeRequestService.submit()}).
 */
@Component
public class RequireDetailedJustificationForHighImpactSubmissionHandler implements ChangeRequestTransitionHandler {

    private static final int MINIMUM_REASON_LENGTH = 40;

    @Override
    public void beforeTransition(ChangeRequestTransitionContext context) {
        if (context.toStatus() != ChangeRequestStatus.SUBMITTED) {
            return;
        }

        ChangeRequest changeRequest = context.changeRequest();
        if (!isHighImpact(changeRequest)) {
            return;
        }

        String reason = changeRequest.getReason();
        if (reason == null || reason.trim().length() < MINIMUM_REASON_LENGTH) {
            throw new FormCraftException(
                    "High-impact change requests need a reason of at least "
                            + MINIMUM_REASON_LENGTH + " characters before submission");
        }
    }

    private boolean isHighImpact(ChangeRequest changeRequest) {
        String impact = changeRequest.getImpact();
        boolean flaggedHighImpact = impact != null && impact.toLowerCase(Locale.ROOT).contains("high");

        boolean touchesFlaggedFinishedProduct = changeRequest.getProduct() != null
                && changeRequest.getProduct().isFinishedProduct()
                && changeRequest.getProduct().getAllergenFlags() != null
                && !changeRequest.getProduct().getAllergenFlags().isBlank();

        return flaggedHighImpact || touchesFlaggedFinishedProduct;
    }
}
