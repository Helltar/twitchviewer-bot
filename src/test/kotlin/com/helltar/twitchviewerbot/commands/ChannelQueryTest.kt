package com.helltar.twitchviewerbot.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChannelQueryTest {

    private val list = listOf("emiru", "emily", "mana", "alpha", "abby", "zed")

    private fun select(vararg arguments: String, userChannels: List<String> = list): ChannelSelection {
        val parsed = ChannelQuery.parse(arguments.toList())
        assertIs<ChannelQuery.ParseResult.Valid>(parsed)
        return parsed.query.select(userChannels)
    }

    private fun invalid(vararg arguments: String): List<String> {
        val parsed = ChannelQuery.parse(arguments.toList())
        assertIs<ChannelQuery.ParseResult.Invalid>(parsed)
        return parsed.arguments
    }

    @Test
    fun `no arguments take the whole list`() {
        assertEquals(ChannelSelection(list), select())
    }

    @Test
    fun `names are taken as they are, listed or not`() {
        assertEquals(ChannelSelection(listOf("foo", "emiru"), setOf("foo", "emiru")), select("Foo", "emiru"))
    }

    @Test
    fun `repeated names and overlapping prefixes give each channel once`() {
        assertEquals(listOf("foo"), select("foo", "FOO", "foo").channels)
        assertEquals(listOf("emiru", "emily"), select("em.", "emi.", "e.").channels)
        assertEquals(listOf("emiru", "emily"), select("emiru", "em.").channels)
    }

    @Test
    fun `prefixes from several filters add up`() {
        assertEquals(listOf("mana", "alpha", "abby"), select("ma.", "a.").channels)
    }

    @Test
    fun `exclusions alone apply to the whole list`() {
        assertEquals(listOf("mana", "zed"), select("!em.", "!a.").channels)
    }

    @Test
    fun `exclusions narrow the prefixes`() {
        assertEquals(listOf("emily"), select("em.", "!emir.").channels)
    }

    @Test
    fun `contradicting filters leave nothing`() {
        assertEquals(emptyList(), select("a.", "!a.").channels)
        assertEquals(emptyList(), select("!a.", "a.").channels)
        assertEquals(emptyList(), select("em.", "!e.").channels)
    }

    @Test
    fun `exclusions apply to names too`() {
        assertEquals(ChannelSelection(emptyList(), emptySet()), select("abby", "!a."))
        assertEquals(ChannelSelection(listOf("foo"), setOf("foo")), select("abby", "foo", "!ab."))
    }

    @Test
    fun `everything excluded leaves nothing`() {
        assertEquals(emptyList(), select("!e.", "!m.", "!a.", "!z.").channels)
    }

    @Test
    fun `a filter over an empty list leaves nothing`() {
        assertEquals(emptyList(), select("em.", userChannels = emptyList()).channels)
        assertEquals(emptyList(), select("!em.", userChannels = emptyList()).channels)
    }

    @Test
    fun `names need no list`() {
        assertEquals(listOf("foo"), select("foo", userChannels = emptyList()).channels)
    }

    @Test
    fun `channels from a prefix are not named`() {
        assertEquals(setOf("zed"), select("em.", "zed").named)
    }

    @Test
    fun `malformed arguments are all reported`() {
        assertEquals(listOf("a", "!.", ".", "!", "a..", "!!a.", "em-", "@x"), invalid("a", "!.", ".", "!", "a..", "!!a.", "em-", "@x"))
    }

    @Test
    fun `one malformed argument rejects the rest`() {
        assertEquals(listOf("b@d"), invalid("emiru", "b@d", "em."))
    }

    @Test
    fun `length limits`() {
        val longest = "a".repeat(25)
        assertEquals(listOf(longest), select(longest).channels)
        assertEquals(listOf("a".repeat(26)), invalid("a".repeat(26)))
        assertEquals(listOf("abby", "alpha").sorted(), select("a.").channels.sorted())
        assertEquals(listOf("${"a".repeat(26)}."), invalid("${"a".repeat(26)}."))
    }

    @Test
    fun `too many arguments`() {
        assertIs<ChannelQuery.ParseResult.TooMany>(ChannelQuery.parse(List(ChannelQuery.MAX_ARGUMENTS + 1) { "ch$it" }))
        assertIs<ChannelQuery.ParseResult.Valid>(ChannelQuery.parse(List(ChannelQuery.MAX_ARGUMENTS) { "ch$it" }))
    }
}
