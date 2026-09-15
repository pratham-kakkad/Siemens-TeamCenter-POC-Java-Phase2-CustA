package fr.formcraft.extensions.changerequest;

import fr.formcraft.common.exception.FormCraftException;
import fr.formcraft.model.enums.ChangeRequestStatus;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionContext;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionHandler;
import org.springframework.stereotype.Component;

/**
 * Nordic Snacks Co. policy: every change request must document its impact before
 * it can be submitted for review — a reason for the change isn't enough on its own,
 * reviewers also need to know what it affects. Plugged into Customization Gate 1;
 * vetoes the SUBMITTED transition (the first hop of {@code ChangeRequestService.submit()}).
 */
@Component
public class RequireImpactNotesBeforeSubmissionHandler implements ChangeRequestTransitionHandler {

    @Override
    public void beforeTransition(ChangeRequestTransitionContext context) {
        if (context.toStatus() != ChangeRequestStatus.SUBMITTED) {
            return;
        }

        String impact = context.changeRequest().getImpact();
        if (impact == null || impact.isBlank()) {
            throw new FormCraftException("Change requests must document their impact before submission");
        }
    }
}
