package fr.formcraft.extensions.customization;

import fr.formcraft.model.entity.CustomAttributeDefinition;
import fr.formcraft.model.enums.CustomAttributeType;
import fr.formcraft.model.enums.ProductType;
import fr.formcraft.sdk.attributes.CustomAttributeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Registers Nordic Snacks Co.'s Gate 2 custom attributes through core's own
 * {@code CustomAttributeService} bean on startup, instead of a human calling the
 * REST API by hand. Gate 2 has no Java interface to implement (unlike Gate 1); a
 * "customization" here just means rows in core's {@code product_attribute_definitions}
 * table, created via the exact same service core's own REST controller calls —
 * reachable here purely because this jar shares core's Spring context at runtime.
 *
 * <p>Idempotent: checks what's already registered before defining anything, so
 * re-running it on every restart is safe.
 */
@Component
public class NordicSnacksCustomizationSeeder implements ApplicationRunner {

    private static final Log log = LogFactory.getLog(NordicSnacksCustomizationSeeder.class);

    private final CustomAttributeService customAttributeService;

    public NordicSnacksCustomizationSeeder(CustomAttributeService customAttributeService) {
        this.customAttributeService = customAttributeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedCustomAttributes();
        } catch (Exception e) {
            log.error("Nordic Snacks: failed to seed Gate 2 custom attributes — leaving existing state as-is", e);
        }
    }

    private void seedCustomAttributes() {
        Set<String> existingKeys = new HashSet<>();
        for (CustomAttributeDefinition def : customAttributeService.listDefinitions()) {
            existingKeys.add(def.getAttributeKey());
        }

        defineAttributeIfAbsent(existingKeys, "nordic_organic_certified",
                "Nordic Organic Certified", CustomAttributeType.BOOLEAN, false, ProductType.RAW_MATERIAL, null);

        defineAttributeIfAbsent(existingKeys, "recyclable_packaging_pct",
                "Recyclable Packaging %", CustomAttributeType.NUMBER, false, ProductType.PACKAGING, null);

        defineAttributeIfAbsent(existingKeys, "eu_novel_food_status",
                "EU Novel Food Status", CustomAttributeType.STRING, false, ProductType.FINISHED_PRODUCT,
                "^(NOT_APPLICABLE|PENDING|APPROVED)$");
    }

    private void defineAttributeIfAbsent(Set<String> existingKeys, String key, String label,
                                          CustomAttributeType type, boolean required,
                                          ProductType appliesTo, String validationRegex) {
        if (existingKeys.contains(key)) {
            log.info("Nordic Snacks: custom attribute '" + key + "' already defined, skipping");
            return;
        }

        CustomAttributeDefinition definition = new CustomAttributeDefinition();
        definition.setAttributeKey(key);
        definition.setLabel(label);
        definition.setDataType(type);
        definition.setRequired(required);
        definition.setAppliesToProductType(appliesTo);
        definition.setValidationRegex(validationRegex);
        customAttributeService.defineAttribute(definition);
        log.info("Nordic Snacks: defined custom attribute '" + key + "'");
    }
}
