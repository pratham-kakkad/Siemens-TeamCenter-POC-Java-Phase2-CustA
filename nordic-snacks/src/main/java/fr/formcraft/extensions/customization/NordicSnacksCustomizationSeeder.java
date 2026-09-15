package fr.formcraft.extensions.customization;

import fr.formcraft.model.entity.AccessRule;
import fr.formcraft.model.entity.CustomAttributeDefinition;
import fr.formcraft.model.entity.EventActionRule;
import fr.formcraft.model.entity.ReportTemplate;
import fr.formcraft.model.enums.CustomAttributeType;
import fr.formcraft.model.enums.EventActionType;
import fr.formcraft.model.enums.EventType;
import fr.formcraft.model.enums.NotificationCategory;
import fr.formcraft.model.enums.ProductType;
import fr.formcraft.model.enums.RuleEffect;
import fr.formcraft.model.enums.TargetEntity;
import fr.formcraft.model.enums.UserRole;
import fr.formcraft.sdk.access.AccessRuleService;
import fr.formcraft.sdk.attributes.CustomAttributeService;
import fr.formcraft.sdk.events.EventActionRuleService;
import fr.formcraft.sdk.report.ReportTemplateService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers Nordic Snacks Co.'s config-driven customizations — Gates 2-5 — through
 * core's own SDK services on startup, instead of a human clicking through the REST
 * API by hand. Gates 2-5 have no Java interface to implement (unlike Gate 1); a
 * "customization" there just means rows in core's tables, created via the same
 * {@code CustomAttributeService}/{@code EventActionRuleService}/{@code AccessRuleService}/
 * {@code ReportTemplateService} beans core's own REST controllers call — reachable
 * here purely because this jar shares core's Spring context at runtime.
 *
 * <p>Every seed step is idempotent (checked against what's already registered) and
 * independently try/caught, so a bad or already-applied seed never prevents the app
 * from starting or blocks the other gates from seeding.
 */
@Component
public class NordicSnacksCustomizationSeeder implements ApplicationRunner {

    private static final Log log = LogFactory.getLog(NordicSnacksCustomizationSeeder.class);

    private final CustomAttributeService customAttributeService;
    private final EventActionRuleService eventActionRuleService;
    private final AccessRuleService accessRuleService;
    private final ReportTemplateService reportTemplateService;

    public NordicSnacksCustomizationSeeder(CustomAttributeService customAttributeService,
                                            EventActionRuleService eventActionRuleService,
                                            AccessRuleService accessRuleService,
                                            ReportTemplateService reportTemplateService) {
        this.customAttributeService = customAttributeService;
        this.eventActionRuleService = eventActionRuleService;
        this.accessRuleService = accessRuleService;
        this.reportTemplateService = reportTemplateService;
    }

    @Override
    public void run(ApplicationArguments args) {
        runSafely("Gate 2 (custom attributes)", this::seedCustomAttributes);
        runSafely("Gate 3 (event action rules)", this::seedEventActionRules);
        runSafely("Gate 4 (access rules)", this::seedAccessRules);
        runSafely("Gate 5 (report templates)", this::seedReportTemplates);
    }

    private void runSafely(String label, Runnable seedStep) {
        try {
            seedStep.run();
        } catch (Exception e) {
            log.error("Nordic Snacks: failed to seed " + label + " — leaving existing state as-is", e);
        }
    }

    // ── Gate 2: custom attributes on Product ────────────────────────────────

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

    // ── Gate 3: event -> notification bindings ──────────────────────────────

    private void seedEventActionRules() {
        Set<String> existingSignatures = new HashSet<>();
        for (EventActionRule rule : eventActionRuleService.listRules()) {
            existingSignatures.add(signatureOf(rule.getEventType(), rule.getActionType(),
                    rule.getTargetRole(), rule.getConditionProductType()));
        }

        // Additive on top of core's defaults (which already notify the initiating user):
        // Quality also wants to hear about every rejection, regardless of product type.
        createRuleIfAbsent(existingSignatures, EventType.CHANGE_REQUEST_REJECTED, null,
                EventActionType.NOTIFY_ROLE, UserRole.QUALITY_MANAGER,
                "Nordic Snacks: change request rejected",
                "\"{title}\" was rejected — quality review may be needed.",
                NotificationCategory.CHANGE_REQUEST);

        // And specifically when a finished product's approved change might affect allergen
        // labeling, Quality should be looped in even though core only notifies the requester.
        createRuleIfAbsent(existingSignatures, EventType.CHANGE_REQUEST_APPROVED, ProductType.FINISHED_PRODUCT,
                EventActionType.NOTIFY_ROLE, UserRole.QUALITY_MANAGER,
                "Nordic Snacks: finished product change approved",
                "\"{title}\" was approved for a finished product — confirm allergen labeling is up to date.",
                NotificationCategory.CHANGE_REQUEST);
    }

    private void createRuleIfAbsent(Set<String> existingSignatures, EventType eventType,
                                     ProductType conditionProductType, EventActionType actionType,
                                     UserRole targetRole, String title, String messageTemplate,
                                     NotificationCategory category) {
        String signature = signatureOf(eventType, actionType, targetRole, conditionProductType);
        if (existingSignatures.contains(signature)) {
            log.info("Nordic Snacks: event action rule '" + signature + "' already registered, skipping");
            return;
        }

        EventActionRule rule = new EventActionRule();
        rule.setEventType(eventType);
        rule.setConditionProductType(conditionProductType);
        rule.setActionType(actionType);
        rule.setTargetRole(targetRole);
        rule.setNotificationTitle(title);
        rule.setMessageTemplate(messageTemplate);
        rule.setNotificationCategory(category);
        rule.setEnabled(true);
        eventActionRuleService.createRule(rule);
        log.info("Nordic Snacks: registered event action rule '" + signature + "'");
    }

    private String signatureOf(EventType eventType, EventActionType actionType,
                                UserRole targetRole, ProductType conditionProductType) {
        return eventType + "|" + actionType + "|" + targetRole + "|" + conditionProductType;
    }

    // ── Gate 4: role/action access rules ────────────────────────────────────

    private void seedAccessRules() {
        Set<String> existingPairs = new HashSet<>();
        for (AccessRule rule : accessRuleService.listRules()) {
            existingPairs.add(rule.getRole() + "|" + rule.getActionKey());
        }

        // View-only and purchasing users shouldn't be able to decide change requests or
        // close non-conformances at Nordic Snacks Co. — PLM/quality managers and admins
        // keep the (unrestricted, default-allow) ability to do both.
        denyIfAbsent(existingPairs, UserRole.VIEWER, "CHANGE_REQUEST_DECIDE");
        denyIfAbsent(existingPairs, UserRole.PURCHASING, "CHANGE_REQUEST_DECIDE");
        denyIfAbsent(existingPairs, UserRole.VIEWER, "NON_CONFORMANCE_CLOSE");
        denyIfAbsent(existingPairs, UserRole.PURCHASING, "NON_CONFORMANCE_CLOSE");
    }

    private void denyIfAbsent(Set<String> existingPairs, UserRole role, String actionKey) {
        String pair = role + "|" + actionKey;
        if (existingPairs.contains(pair)) {
            log.info("Nordic Snacks: access rule '" + pair + "' already registered, skipping");
            return;
        }

        AccessRule rule = new AccessRule();
        rule.setRole(role);
        rule.setActionKey(actionKey);
        rule.setEffect(RuleEffect.DENY);
        accessRuleService.defineRule(rule);
        log.info("Nordic Snacks: registered access rule '" + pair + "' (DENY)");
    }

    // ── Gate 5: report templates ─────────────────────────────────────────────

    private void seedReportTemplates() {
        Set<String> existingKeys = new HashSet<>();
        for (ReportTemplate template : reportTemplateService.listTemplates()) {
            existingKeys.add(template.getTemplateKey());
        }

        String key = "NORDIC_ORGANIC_COMPLIANCE";
        if (existingKeys.contains(key)) {
            log.info("Nordic Snacks: report template '" + key + "' already defined, skipping");
            return;
        }

        ReportTemplate template = new ReportTemplate();
        template.setTemplateKey(key);
        template.setName("Nordic Organic & Novel Food Compliance");
        template.setTargetEntity(TargetEntity.PRODUCT);
        template.setFields(List.of("code", "name", "productType", "nordic_organic_certified", "eu_novel_food_status"));
        template.setLabels(Map.of(
                "code", "Code",
                "name", "Name",
                "productType", "Type",
                "nordic_organic_certified", "Organic Certified",
                "eu_novel_food_status", "EU Novel Food Status"));
        reportTemplateService.defineTemplate(template);
        log.info("Nordic Snacks: defined report template '" + key + "'");
    }
}
