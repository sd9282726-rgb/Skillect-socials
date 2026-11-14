package com.example.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryCatalogTest {
    @Test
    fun supportedCountriesIncludesGlobalIsoCatalogWithoutDuplicates() {
        val countries = AppConfig.supportedCountries
        assertTrue("Expected a global country catalog", countries.size >= 200)
        assertEquals("US", countries.first().isoCode)
        assertEquals(countries.size, countries.map { it.isoCode }.distinct().size)
        assertFalse(countries.any { it.name.isBlank() })
    }
}
