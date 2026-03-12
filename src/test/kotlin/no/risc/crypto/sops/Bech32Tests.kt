package no.risc.crypto.sops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Bech32Tests {
    @Test
    fun `when random randomBech32 is created, the correct length is returned`() {
        // 15= prefix(7)+"1"(1)+char(1)+checksum(6)
        assertEquals(15, randomBech32("prefix-", 1).length)
        // 20= prefix(7)+"1"(1)+char(6)+checksum(6)
        assertEquals(20, randomBech32("prefix-", 6).length)
    }
}
