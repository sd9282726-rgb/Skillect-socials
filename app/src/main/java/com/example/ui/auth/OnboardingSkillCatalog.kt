package com.example.ui.auth

/** Shared source of truth for onboarding and profile editing. */
object OnboardingSkillCatalog {
    val categories: List<Pair<String, List<String>>> = listOf(
        "TECH & ENGINEERING" to listOf("Kotlin", "App Dev", "AI Engineering", "Web Design", "Robotics", "Product Strategy"),
        "CREATIVE & ARTS" to listOf("UI/UX Design", "Photography", "Filmmaking", "Illustration", "Animation", "3D Art"),
        "BUSINESS & WORDS" to listOf("Marketing", "Finance", "Writing", "Public Speaking", "Research", "Community Lead"),
    )
    val all: List<String> = categories.flatMap { it.second }.distinct()
    val interests: List<String> = listOf("Technology", "Education", "Entrepreneurship", "Design", "Creative Arts", "Science")
}
