package com.atonai.data.perms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermMatcherTest {

    @Test
    fun exact_pattern_matches() {
        assertTrue(PermMatcher.matches("lib/parser.kt", "lib/parser.kt"))
    }

    @Test
    fun star_spans_directories() {
        assertTrue(PermMatcher.matches("lib/**", "lib/a/b/c.kt"))
        assertTrue(PermMatcher.matches("lib/*", "lib/a/b.kt"))
    }

    @Test
    fun double_star_matches_everything() {
        assertTrue(PermMatcher.matches("**", "anything/at/all"))
    }

    @Test
    fun host_pattern_matches_subdomain() {
        assertTrue(PermMatcher.matches("*.example.com", "api.example.com"))
        assertFalse(PermMatcher.matches("*.example.com", "example.org"))
    }

    @Test
    fun no_rule_returns_null() {
        assertNull(PermMatcher.match(emptyList(), Action.WRITE, "a.kt"))
    }

    @Test
    fun more_specific_rule_wins() {
        val rules = listOf(
            PermRule(Action.WRITE, "**", PermRule.ASK),
            PermRule(Action.WRITE, "lib/**", PermRule.ALLOW),
        )
        assertEquals(PermRule.ALLOW, PermMatcher.match(rules, Action.WRITE, "lib/x.kt")?.decision)
        assertEquals(PermRule.ASK, PermMatcher.match(rules, Action.WRITE, "src/x.kt")?.decision)
    }

    @Test
    fun decision_falls_back_when_no_rule_matches() {
        assertEquals(PermRule.ASK, PermMatcher.decide(emptyList(), Action.WRITE, "x", PermRule.ASK))
    }

    @Test
    fun read_defaults_to_allow_and_write_to_ask() {
        assertEquals(PermRule.ALLOW, PermMatcher.policyDefault(Action.READ))
        assertEquals(PermRule.ALLOW, PermMatcher.policyDefault(Action.NONE))
        assertEquals(PermRule.ASK, PermMatcher.policyDefault(Action.WRITE))
        assertEquals(PermRule.ASK, PermMatcher.policyDefault(Action.SHELL))
    }

    @Test
    fun specificity_ranks_longer_patterns_higher() {
        assertTrue(PermMatcher.specificity("lib/**") > PermMatcher.specificity("**"))
    }

    @Test
    fun action_must_match() {
        val rules = listOf(PermRule(Action.SHELL, "git *", PermRule.ALLOW))
        assertNull(PermMatcher.match(rules, Action.WRITE, "git status"))
    }
}
