package fr.formcraft.extensions.changerequest;

import fr.formcraft.common.exception.FormCraftException;
import fr.formcraft.model.enums.ChangeRequestStatus;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionContext;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionHandler;
import org.springframework.stereotype.Component;

/**
 * Example client extension plugged into Customization Gate 1. Lives in the
 * formcraft-plm-customs-nordic-snacks module, in its own repository, built and
 * packaged entirely independently of formcraft-plm-core — demonstrates a
 * client-specific rule: rejecting a change request requires a decision comment
 * explaining why. Discovered at runtime purely by being a {@code @Component} on
 * the classpath; no core code references this class.
 */
@Component
public class RequireCommentOnRejectionHandler implements ChangeRequestTransitionHandler {

    @Override
    public void beforeTransition(ChangeRequestTransitionContext context) {
        if (context.toStatus() == ChangeRequestStatus.REJECTED && isBlank(context.changeRequest().getDecisionComment())) {
            throw new FormCraftException("A decision comment is required when rejecting a change request");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
