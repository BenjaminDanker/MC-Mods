package com.silver.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PermissionEvaluatorTest {
    private static final ServerId SKY = ServerId.of("sky-island");
    private static final PermissionNode RESTART = PermissionNode.of("server.restart");
    private static final AuthorizationScope GLOBAL = AuthorizationScope.global();

    @Test
    void serverScopeBeatsGlobalEvenWhenGlobalPatternIsMoreSpecific() {
        var rules = List.of(
                rule("server.restart", GLOBAL, RuleOrigin.DIRECT_USER, PermissionEffect.ALLOW),
                rule("server.*", AuthorizationScope.server(SKY), RuleOrigin.ROLE, PermissionEffect.DENY));

        assertFalse(PermissionEvaluator.evaluate(rules, RESTART, SKY).allowed());
    }

    @Test
    void moreSpecificPatternBeatsBroaderPattern() {
        var rules = List.of(
                rule("*", GLOBAL, RuleOrigin.DIRECT_USER, PermissionEffect.DENY),
                rule("server.*", GLOBAL, RuleOrigin.ROLE, PermissionEffect.ALLOW));

        var decision = PermissionEvaluator.evaluate(rules, RESTART, SKY);
        assertTrue(decision.allowed());
        assertEquals("server.*", decision.winningPattern().orElseThrow().value());
    }

    @Test
    void exactNodeBeatsMatchingWildcard() {
        var rules = List.of(
                rule("server.*", GLOBAL, RuleOrigin.DIRECT_USER, PermissionEffect.DENY),
                rule("server.restart", GLOBAL, RuleOrigin.ROLE, PermissionEffect.ALLOW));

        var decision = PermissionEvaluator.evaluate(rules, RESTART, SKY);
        assertTrue(decision.allowed());
        assertEquals("server.restart", decision.winningPattern().orElseThrow().value());
        assertEquals(AuthorizationDecision.LossReason.LESS_SPECIFIC_PATTERN,
                decision.consideredRules().stream().filter(trace -> !trace.winner()).findFirst().orElseThrow().reason());
    }

    @Test
    void directUserRuleBeatsRoleRuleAtEqualScopeAndSpecificity() {
        var rules = List.of(
                rule("server.restart", GLOBAL, RuleOrigin.ROLE, PermissionEffect.DENY),
                rule("server.restart", GLOBAL, RuleOrigin.DIRECT_USER, PermissionEffect.ALLOW));

        var decision = PermissionEvaluator.evaluate(rules, RESTART, SKY);
        assertTrue(decision.allowed());
        assertEquals(RuleOrigin.DIRECT_USER, decision.sourceType().orElseThrow());
        assertEquals(AuthorizationDecision.LossReason.ROLE_BEHIND_DIRECT_RULE,
                decision.consideredRules().stream().filter(trace -> !trace.winner()).findFirst().orElseThrow().reason());
    }

    @Test
    void denyWinsWhenAllHigherPrecedenceFieldsTie() {
        var rules = List.of(
                rule("server.restart", GLOBAL, RuleOrigin.DIRECT_USER, PermissionEffect.ALLOW),
                rule("server.restart", GLOBAL, RuleOrigin.DIRECT_USER, PermissionEffect.DENY));

        var decision = PermissionEvaluator.evaluate(rules, RESTART, SKY);
        assertFalse(decision.allowed());
        assertEquals(PermissionEffect.DENY, decision.matchedRule().orElseThrow().effect());
    }

    @Test
    void wildcardIsTerminalAndHonorsComponentBoundary() {
        PermissionPattern pattern = PermissionPattern.of("SERVER.*");
        assertTrue(pattern.matches(PermissionNode.of("server.restart")));
        assertTrue(pattern.matches(PermissionNode.of("server.restart.local")));
        assertFalse(pattern.matches(PermissionNode.of("servers.restart")));
        assertEquals("server.*", pattern.value());
    }

    @Test
    void noMatchingRuleDefaultsToDeny() {
        assertFalse(PermissionEvaluator.evaluate(List.of(), RESTART, SKY).allowed());
        assertFalse(PermissionEvaluator.evaluate(
                List.of(rule("player.*", GLOBAL, RuleOrigin.ROLE, PermissionEffect.ALLOW)), RESTART, SKY).allowed());
    }

    @Test
    void multipleRoleRulesAreResolvedDeterministically() {
        var rules = List.of(
                rule("server.*", GLOBAL, RuleOrigin.ROLE, PermissionEffect.ALLOW),
                rule("server.restart", GLOBAL, RuleOrigin.ROLE, PermissionEffect.DENY),
                rule("server.reload", GLOBAL, RuleOrigin.ROLE, PermissionEffect.ALLOW));

        assertFalse(PermissionEvaluator.evaluate(rules, RESTART, SKY).allowed());
        assertTrue(PermissionEvaluator.evaluate(rules, PermissionNode.of("server.reload"), SKY).allowed());
    }

    @Test
    void dynamicMultipleRolesRespectAssignmentScopeAndDoNotLeakAcrossServers() {
        RoleId moderator = RoleId.of("moderator");
        RoleId developer = RoleId.of("developer");
        var assignments = List.of(
                new RoleAssignment(moderator, AuthorizationScope.server(SKY)),
                new RoleAssignment(developer, GLOBAL));
        var rolePermissions = List.of(
                new RolePermission(moderator, PermissionPattern.of("player.kick"), GLOBAL, PermissionEffect.ALLOW),
                new RolePermission(developer, PermissionPattern.of("developer.debug"), GLOBAL, PermissionEffect.ALLOW),
                new RolePermission(developer, PermissionPattern.of("server.*"),
                        AuthorizationScope.server(ServerId.of("magic")), PermissionEffect.DENY));

        var skyRules = EffectivePermissionRules.forServer(assignments, rolePermissions, List.of(), SKY);
        assertTrue(PermissionEvaluator.evaluate(skyRules, PermissionNode.of("player.kick"), SKY).allowed());
        assertTrue(PermissionEvaluator.evaluate(skyRules, PermissionNode.of("developer.debug"), SKY).allowed());
        assertFalse(PermissionEvaluator.evaluate(skyRules, PermissionNode.of("server.stop"), SKY).allowed());
        assertTrue(skyRules.stream().allMatch(rule -> rule.scope().appliesTo(SKY)));

        var magic = ServerId.of("magic");
        var magicRules = EffectivePermissionRules.forServer(assignments, rolePermissions, List.of(), magic);
        assertFalse(PermissionEvaluator.evaluate(magicRules, PermissionNode.of("player.kick"), magic).allowed());
        assertTrue(PermissionEvaluator.evaluate(magicRules, PermissionNode.of("developer.debug"), magic).allowed());
        assertEquals(AuthorizationScope.Type.SERVER,
                magicRules.stream().filter(rule -> rule.pattern().value().equals("server.*"))
                        .findFirst().orElseThrow().scope().type());
    }

    @Test
    void roleAssignmentAndPermissionScopesFollowTheFullInteractionMatrix() {
        RoleId role = RoleId.of("SERVER_MANAGER");
        ServerId magic = ServerId.of("magic");

        var globalAssignmentGlobalPermission = effective(role, GLOBAL, GLOBAL, SKY, magic);
        assertTrue(PermissionEvaluator.evaluate(globalAssignmentGlobalPermission.get(SKY), RESTART, SKY).allowed());
        assertTrue(PermissionEvaluator.evaluate(globalAssignmentGlobalPermission.get(magic), RESTART, magic).allowed());

        var globalAssignmentServerPermission = effective(role, GLOBAL,
                AuthorizationScope.server(SKY), SKY, magic);
        assertTrue(PermissionEvaluator.evaluate(globalAssignmentServerPermission.get(SKY), RESTART, SKY).allowed());
        assertFalse(PermissionEvaluator.evaluate(globalAssignmentServerPermission.get(magic), RESTART, magic).allowed());

        var serverAssignmentGlobalPermission = effective(role, AuthorizationScope.server(SKY), GLOBAL, SKY, magic);
        assertTrue(PermissionEvaluator.evaluate(serverAssignmentGlobalPermission.get(SKY), RESTART, SKY).allowed());
        assertFalse(PermissionEvaluator.evaluate(serverAssignmentGlobalPermission.get(magic), RESTART, magic).allowed());

        assertTrue(PermissionEvaluator.evaluate(effective(role, AuthorizationScope.server(SKY),
                AuthorizationScope.server(SKY), SKY, magic).get(SKY), RESTART, SKY).allowed());
        assertFalse(PermissionEvaluator.evaluate(effective(role, AuthorizationScope.server(SKY),
                AuthorizationScope.server(magic), SKY, magic).get(SKY), RESTART, SKY).allowed());
        assertFalse(PermissionEvaluator.evaluate(effective(role, AuthorizationScope.server(SKY),
                AuthorizationScope.server(magic), SKY, magic).get(magic), RESTART, magic).allowed());
    }

    @Test
    void serverScopeBeatsGlobalExactAndServerWildcardBeatsGlobalDeny() {
        var globalExact = rule("server.restart", GLOBAL, RuleOrigin.ROLE, PermissionEffect.ALLOW);
        var serverWildcard = rule("server.*", AuthorizationScope.server(SKY),
                RuleOrigin.ROLE, PermissionEffect.DENY);
        assertFalse(PermissionEvaluator.evaluate(List.of(globalExact, serverWildcard), RESTART, SKY).allowed());

        var globalDeny = rule("server.restart", GLOBAL, RuleOrigin.DIRECT_USER, PermissionEffect.DENY);
        var serverAllow = rule("server.*", AuthorizationScope.server(SKY), RuleOrigin.ROLE, PermissionEffect.ALLOW);
        assertTrue(PermissionEvaluator.evaluate(List.of(globalDeny, serverAllow), RESTART, SKY).allowed());
    }

    @Test
    void serverDenyOverridesGlobalOwnerWildcardAndExplanationRetainsTheLoser() {
        RoleId owner = RoleId.of("OWNER");
        RoleId moderator = RoleId.of("MODERATOR");
        var globalOwner = PermissionRule.role(PermissionPattern.of("*"), GLOBAL,
                PermissionEffect.ALLOW, owner);
        var scopedDeny = PermissionRule.role(PermissionPattern.of("server.restart"),
                AuthorizationScope.server(SKY), PermissionEffect.DENY, moderator);

        var decision = PermissionEvaluator.evaluate(List.of(globalOwner, scopedDeny), RESTART, SKY, 42);
        assertFalse(decision.allowed());
        assertEquals(42, decision.authorizationRevision());
        assertEquals(RESTART, decision.requestedPermission());
        assertEquals(AuthorizationScope.server(SKY), decision.requestedScope());
        assertEquals(moderator, decision.matchedRule().orElseThrow().sourceRole().orElseThrow());
        assertEquals(AuthorizationDecision.LossReason.LOWER_SCOPE,
                decision.consideredRules().stream().filter(trace -> !trace.winner()).findFirst().orElseThrow().reason());
    }

    @Test
    void conflictingRoleRulesUseDenyAndSpecificityWithoutRolePriority() {
        var administrator = PermissionRule.role(PermissionPattern.of("player.*"), GLOBAL,
                PermissionEffect.ALLOW, RoleId.of("ADMIN"));
        var moderator = PermissionRule.role(PermissionPattern.of("player.kick"), GLOBAL,
                PermissionEffect.DENY, RoleId.of("MODERATOR"));
        assertFalse(PermissionEvaluator.evaluate(List.of(administrator, moderator),
                PermissionNode.of("player.kick"), SKY).allowed());
    }

    private static Map<ServerId, List<PermissionRule>> effective(RoleId role,
            AuthorizationScope assignmentScope, AuthorizationScope permissionScope,
            ServerId... destinations) {
        var assignments = List.of(new RoleAssignment(role, assignmentScope));
        var permissions = List.of(new RolePermission(role, PermissionPattern.of("server.restart"),
                permissionScope, PermissionEffect.ALLOW));
        Map<ServerId, List<PermissionRule>> result = new java.util.HashMap<>();
        for (ServerId destination : destinations) {
            result.put(destination, EffectivePermissionRules.forServer(assignments, permissions, List.of(), destination));
        }
        return result;
    }

    private static PermissionRule rule(
            String pattern, AuthorizationScope scope, RuleOrigin origin, PermissionEffect effect) {
            return origin == RuleOrigin.ROLE
                    ? PermissionRule.role(PermissionPattern.of(pattern), scope, effect, RoleId.of("TEST"))
                    : PermissionRule.direct(PermissionPattern.of(pattern), scope, effect);
    }

}
