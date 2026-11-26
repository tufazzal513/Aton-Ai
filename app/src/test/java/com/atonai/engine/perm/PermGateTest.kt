package com.atonai.engine.perm

import com.atonai.data.perms.Action
import com.atonai.data.perms.DeniedException
import com.atonai.data.perms.PermRule
import com.atonai.data.perms.PermissionReq
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermGateTest {

    private fun gate(
        rules: List<PermRule>,
        answer: String,
        asked: MutableList<PermissionReq> = mutableListOf(),
    ): PermGate = PermGate(
        rules = { rules },
        ask = { req -> asked.add(req); answer },
    )

    @Test
    fun allow_rule_returns_without_asking() = runBlocking {
        val asked = mutableListOf<PermissionReq>()
        val g = gate(listOf(PermRule(Action.WRITE, "lib/**", PermRule.ALLOW)), PermissionReq.DENY, asked)
        g.permit(Action.WRITE, "lib/x.kt")
        assertTrue(asked.isEmpty())
    }

    @Test
    fun deny_rule_throws() = runBlocking {
        val g = gate(listOf(PermRule(Action.SHELL, "rm -rf *", PermRule.DENY)), PermissionReq.ALLOW_ONCE)
        var threw = false
        try {
            g.permit(Action.SHELL, "rm -rf /")
        } catch (e: DeniedException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun ask_rule_surfaces_a_question_and_allow_once_proceeds() = runBlocking {
        val asked = mutableListOf<PermissionReq>()
        val g = gate(emptyList(), PermissionReq.ALLOW_ONCE, asked)
        g.permit(Action.WRITE, "a.kt")
        assertEquals(1, asked.size)
        assertEquals(Action.WRITE, asked.single().action)
        assertEquals(listOf("a.kt"), asked.single().resources)
    }

    @Test
    fun deny_answer_throws() = runBlocking {
        val g = gate(emptyList(), PermissionReq.DENY)
        var threw = false
        try {
            g.permit(Action.WRITE, "a.kt")
        } catch (e: DeniedException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun read_is_allowed_by_policy_default() = runBlocking {
        val g = gate(emptyList(), PermissionReq.DENY)
        // no rule, but read defaults to allow, so no exception and no prompt
        g.permit(Action.READ, "a.kt")
    }

    @Test
    fun default_options_match_the_recovered_literals() {
        assertEquals(
            listOf("Allow once", "Always allow", "Deny"),
            PermissionReq.defaultOptions,
        )
    }

    @Test
    fun permissive_gate_never_asks() = runBlocking {
        PermGate.permissive().permit(Action.SHELL, "anything")
    }

    @Test
    fun hostile_gate_denies() = runBlocking {
        var threw = false
        try {
            PermGate.hostile().permit(Action.WRITE, "a.kt")
        } catch (e: DeniedException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun permit_path_picks_the_action() = runBlocking {
        val asked = mutableListOf<PermissionReq>()
        val g = gate(emptyList(), PermissionReq.ALLOW_ONCE, asked)
        g.permitPath("a.kt", write = true)
        assertEquals(Action.WRITE, asked.single().action)
    }
}
