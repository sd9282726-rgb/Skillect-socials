package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.config.AppConfig
import com.example.core.config.CountryOption
import java.util.Calendar

/**
 * Sign-up date of birth (Day / Month / Year dropdowns) and searchable country with flags.
 * Composes ISO YYYY-MM-DD for [AppConfig.isEligibleAge16Plus] checks.
 */
@Composable
fun SignUpDobCountrySection(
    birthDateIso: String,
    selectedCountry: CountryOption,
    onBirthDateIsoChange: (String) -> Unit,
    onCountryChange: (CountryOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var birthDay by remember { mutableStateOf("") }
    var birthMonth by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }

    fun applyBirthParts(day: String = birthDay, month: String = birthMonth, year: String = birthYear) {
        birthDay = day
        birthMonth = month
        birthYear = year
        val iso = OnboardingValidation.composeBirthDateIso(day, month, year)
        onBirthDateIsoChange(iso.orEmpty())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Date of birth",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Select your date of birth to confirm eligibility.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_dob_input"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var dayMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { dayMenu = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = birthDay.ifBlank { "Day" },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        color = if (birthDay.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                    (1..31).forEach { d ->
                        DropdownMenuItem(
                            text = { Text("%02d".format(d)) },
                            onClick = {
                                dayMenu = false
                                applyBirthParts(day = "%02d".format(d))
                            },
                        )
                    }
                }
            }
            var monthMenu by remember { mutableStateOf(false) }
            val monthLabels = listOf(
                "01" to "Jan", "02" to "Feb", "03" to "Mar", "04" to "Apr",
                "05" to "May", "06" to "Jun", "07" to "Jul", "08" to "Aug",
                "09" to "Sep", "10" to "Oct", "11" to "Nov", "12" to "Dec",
            )
            Box(modifier = Modifier.weight(1.2f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { monthMenu = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    val label = monthLabels.firstOrNull { it.first == birthMonth }?.second ?: "Month"
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        color = if (birthMonth.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                    monthLabels.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                monthMenu = false
                                applyBirthParts(month = value)
                            },
                        )
                    }
                }
            }
            var yearMenu by remember { mutableStateOf(false) }
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            Box(modifier = Modifier.weight(1.2f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { yearMenu = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = birthYear.ifBlank { "Year" },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        color = if (birthYear.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    ((currentYear - 16) downTo 1900).forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y.toString()) },
                            onClick = {
                                yearMenu = false
                                applyBirthParts(year = y.toString())
                            },
                        )
                    }
                }
            }
        }
        if (birthDateIso.isNotBlank() && !AppConfig.isEligibleAge16Plus(birthDateIso)) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You do not meet the minimum age requirement to join Skillect.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Country / Region",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { countryMenuOpen = true }
                    .testTag("auth_country_picker"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Text(
                        text = "${OnboardingValidation.countryFlagEmoji(selectedCountry.isoCode)}  ${selectedCountry.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            DropdownMenu(
                expanded = countryMenuOpen,
                onDismissRequest = {
                    countryMenuOpen = false
                    countryQuery = ""
                },
            ) {
                OutlinedTextField(
                    value = countryQuery,
                    onValueChange = { countryQuery = it },
                    placeholder = { Text("Search countries") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("country_search_input"),
                )
                val filtered = AppConfig.supportedCountries.filter {
                    countryQuery.isBlank() ||
                        it.name.contains(countryQuery, ignoreCase = true) ||
                        it.isoCode.contains(countryQuery, ignoreCase = true)
                }
                filtered.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text("${OnboardingValidation.countryFlagEmoji(option.isoCode)}  ${option.name} (${option.isoCode})")
                        },
                        onClick = {
                            onCountryChange(option)
                            countryMenuOpen = false
                            countryQuery = ""
                        },
                    )
                }
                if (filtered.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No countries match") },
                        onClick = {},
                        enabled = false,
                    )
                }
            }
        }
    }
}
