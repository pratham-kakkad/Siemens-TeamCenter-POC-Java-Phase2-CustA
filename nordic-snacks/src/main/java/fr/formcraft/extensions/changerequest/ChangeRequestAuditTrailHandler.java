package fr.formcraft.extensions.changerequest;

import fr.formcraft.common.constants.RepoConsts;
import fr.formcraft.repo.audit.AuditService;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionContext;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionHandler;
import org.springframework.stereotype.Component;

/**
 * Nordic Snacks Co. wants every ChangeRequest transition audited — not just the
 * CREATE/APPROVE/REJECT/IMPLEMENT actions core already logs today (submitting a
 * request or starting its review currently write no audit entry). Non-vetoing:
 * demonstrates that a Gate 1 handler can be a plain observer rather than a guard,
 * and that it can inject and call core's own services directly, since a Customs
 * component lives in the same Spring context as core at runtime.
 */
@Component
public class ChangeRequestAuditTrailHandler implements ChangeRequestTransitionHandler {

    private final AuditService auditService;

    public ChangeRequestAuditTrailHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void afterTransition(ChangeRequestTransitionContext context) {
        auditService.logAction(
                context.changeRequest().getId(),
                "ChangeRequest",
                RepoConsts.AUDIT_TRANSITION,
                "Nordic Snacks customization: " + context.fromStatus() + " -> " + context.toStatus());
    }
}
