package com.atonai.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParserTest {

    private val sample = """
    {
      "defaultProviderId": "openai",
      "defaultModelId": "gpt-4.1-mini",
      "providers": [
        {
          "id": "openai",
          "name": "OpenAI",
          "kind": "openai",
          "api": "https://api.openai.com/v1",
          "env": "OPENAI_API_KEY",
          "models": [
            ["gpt-4.1-mini", "GPT-4.1 mini", 1047576, 0, 0.4, 1.6, 0.1, 0.0, 32768],
            ["o4-mini", "o4-mini", 200000, 1, 1.1, 4.4, 0.275, 0.0, 100000]
          ]
        },
        {
          "id": "anthropic",
          "name": "Anthropic",
          "kind": "anthropic",
          "api": "https://api.anthropic.com/v1",
          "env": "ANTHROPIC_API_KEY",
          "models": [
            ["claude-sonnet-4-20250514", "Claude Sonnet 4", 200000, 1, 3.0, 15.0, 0.3, 3.75, 64000]
          ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun parses_providers_and_defaults() {
        val cat = CatalogParser.parse(sample)
        assertEquals(2, cat.providers.size)
        assertEquals("openai", cat.defaultProviderId)
        assertEquals("gpt-4.1-mini", cat.defaultModelId)
        assertEquals("OpenAI", cat.defaultProvider()?.name)
    }

    @Test
    fun lifts_positional_tuple_into_named_fields() {
        val model = CatalogParser.parse(sample).provider("openai")?.model("o4-mini")!!
        assertEquals("o4-mini", model.id)
        assertEquals("o4-mini", model.name)
        assertEquals(200_000L, model.context)
        assertEquals(1, model.reasoning)
        assertTrue(model.supportsReasoning)
        assertEquals(1.1, model.costIn, 0.0)
        assertEquals(4.4, model.costOut, 0.0)
        assertEquals(0.275, model.costCacheRead, 0.0)
        assertEquals(0.0, model.costCacheWrite, 0.0)
        assertEquals(100_000L, model.maxOutput)
    }

    @Test
    fun applies_defaults_for_missing_numbers() {
        val text = """{"providers":[{"id":"p","name":"P","kind":"openai","api":"x",
                        "models":[["m","M"]]}]}"""
        val model = CatalogParser.parse(text).provider("p")?.model("m")!!
        assertEquals(128_000L, model.context)
        assertEquals(0, model.reasoning)
        assertEquals(8_192L, model.maxOutput)
    }

    @Test
    fun drops_malformed_model_but_keeps_the_provider() {
        val text = """{"providers":[{"id":"p","name":"P","kind":"openai","api":"x",
                        "models":[[],["good","Good",1000,0,0,0,0,0,100]]}]}"""
        val cat = CatalogParser.parse(text)
        assertEquals(1, cat.providers.size)
        assertEquals(1, cat.provider("p")!!.models.size)
        assertEquals("good", cat.provider("p")!!.models.first().id)
    }

    @Test
    fun empty_catalog_is_safe() {
        val cat = CatalogParser.parse("""{"providers":[]}""")
        assertTrue(cat.isEmpty())
        assertNull(cat.defaultProvider())
    }
}
