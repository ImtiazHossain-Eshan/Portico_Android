package com.portico.android

import com.portico.android.data.PorticoBackend
import com.portico.android.domain.Property
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What actually leaves the device.
 *
 * kotlinx omits any field equal to its declared default, so a property in USD
 * sent no `currency` key at all and the server refused the record as invalid.
 * Nothing caught it because every test built objects in memory, where a default
 * is indistinguishable from a value.
 *
 * The bridge had already been taught to tolerate three other fields going
 * missing the same way. These assert the payload is complete instead.
 */
class PayloadEncodingTest {

    private val usdProperty = Property(
        id = "p-1", name = "Test", address = "1 Test St",
        country = "Uruguay", region = "Montevideo", type = "Residential",
        sizeSqm = 80.0, purchaseDate = "14 Mar 2025",
        purchasePrice = 100_000.0, initialInvestment = 20_000.0,
        currentValue = 120_000.0
        // currency, financingAmount, photoUris and note all left at their defaults
    )

    private fun encoded(): String = PorticoBackend.json.encodeToString(
        Property.serializer(), usdProperty
    )

    @Test
    fun `a default currency is still sent`() {
        assertTrue(
            "USD is the default, and omitting it is what the server rejected",
            encoded().contains("\"currency\":\"USD\"")
        )
    }

    @Test
    fun `the other defaulted fields survive too`() {
        val json = encoded()
        // Each of these had a server side workaround for the same omission.
        assertTrue("financingAmount missing", json.contains("\"financingAmount\""))
        assertTrue("photoUris missing", json.contains("\"photoUris\""))
        assertTrue("note missing", json.contains("\"note\""))
    }
}
