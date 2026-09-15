package fr.formcraft.extensions.changerequest;

import fr.formcraft.common.exception.FormCraftException;
import fr.formcraft.model.entity.ChangeRequest;
import fr.formcraft.model.entity.Product;
import fr.formcraft.model.enums.ChangeRequestStatus;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionContext;
import fr.formcraft.sdk.workflow.ChangeRequestTransitionHandler;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Nordic Snacks Co. policy: a change request touching an allergen-relevant recipe
 * (the product already carries allergen flags, or the request's own impact notes
 * call out allergens/recipe changes) cannot be approved unless the decision comment
 * records a QA allergen sign-off. Plugged into Customization Gate 1 — vetoes the
 * APPROVED transition, mirroring {@link RequireCommentOnRejectionHandler}'s pattern
 * for the opposite (REJECTED) transition.
 */
@Component
public class EnforceAllergenSignOffBeforeApprovalHandler implements ChangeRequestTransitionHandler {

    private static final String SIGN_OFF_TOKEN = "ALLERGEN-REVIEWED";

    @Override
    public void beforeTransition(ChangeRequestTransitionContext context) {
        if (context.toStatus() != ChangeRequestStatus.APPROVED) {
            return;
        }

        ChangeRequest changeRequest = context.changeRequest();
        if (!isAllergenRelevant(changeRequest)) {
            return;
        }

        String comment = changeRequest.getDecisionComment();
        if (comment == null || !comment.toUpperCase(Locale.ROOT).contains(SIGN_OFF_TOKEN)) {
            throw new FormCraftException(
                    "Allergen-relevant change requests require a QA sign-off in the decision comment "
                            + "(include the token \"" + SIGN_OFF_TOKEN + "\")");
        }
    }

    private boolean isAllergenRelevant(ChangeRequest changeRequest) {
        Product product = changeRequest.getProduct();
        boolean productCarriesAllergens = product != null
                && product.getAllergenFlags() != null
                && !product.getAllergenFlags().isBlank();

        String impact = changeRequest.getImpact();
        boolean impactMentionsAllergens = impact != null
                && (containsIgnoreCase(impact, "allergen") || containsIgnoreCase(impact, "recipe"));

        return productCarriesAllergens || impactMentionsAllergens;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
