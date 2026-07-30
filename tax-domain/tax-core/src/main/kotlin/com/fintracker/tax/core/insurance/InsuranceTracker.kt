package com.fintracker.tax.core.insurance

enum class InsuranceStatus {
    NOT_PURCHASED,
    PURCHASED
}

data class InsuranceItem(
    val id: String,
    val name: String,
    val status: InsuranceStatus,
    val description: String
)

data class InsuranceChecklistSummary(
    val isAllPurchased: Boolean,
    val items: List<InsuranceItem>
)

object InsuranceTracker {

    private val itemsMap = mutableMapOf(
        "TERM" to InsuranceItem("TERM", "Term Insurance", InsuranceStatus.NOT_PURCHASED, "Employer cover lapses on job switch. Personal term policy required."),
        "HEALTH" to InsuranceItem("HEALTH", "Health Insurance", InsuranceStatus.NOT_PURCHASED, "Employer group health cover only. Standalone family health cover required.")
    )

    fun getSummary(): InsuranceChecklistSummary {
        val itemsList = itemsMap.values.toList()
        val allPurchased = itemsList.all { it.status == InsuranceStatus.PURCHASED }
        return InsuranceChecklistSummary(
            isAllPurchased = allPurchased,
            items = itemsList
        )
    }

    fun updateStatus(id: String, status: InsuranceStatus): InsuranceChecklistSummary {
        val existing = itemsMap[id]
        if (existing != null) {
            itemsMap[id] = existing.copy(status = status)
        }
        return getSummary()
    }
}
