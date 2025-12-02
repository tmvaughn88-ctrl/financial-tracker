// DataModels.kt

package com.example.financialtracker

import com.google.firebase.firestore.DocumentId
import java.util.*

enum class Page {
    SPLASH,
    LOGIN,
    TRACKER_SELECTION,
    TRACKER_LIST,
    DASHBOARD,
    ACCOUNT_HISTORY,
    UPCOMING_HISTORY,
    MANAGE_REQUESTS,
    MANAGE_ACCOUNTS,
    FUTURE_SCOPE,
    CALENDAR_SCOPE,
    BUDGET,
    SCANNER,
    GRAPHS
}

data class Tracker(
    @DocumentId val id: String = "",
    val name: String = "",
    val members: List<String> = emptyList(),
    val creatorId: String = "",
    val monthlyBudget: Double = 0.0,
    val weeklyBudget: Double = 0.0
)

data class Account(
    @DocumentId val id: String = "",
    val name: String = "",
    val type: String = ""
)

data class JoinRequest(
    @DocumentId val id: String = "",
    val trackerId: String = "",
    val requestingUserId: String = "",
    val requestingUserEmail: String = ""
)


enum class ConfirmationState {
    NONE,
    NEEDS_CONFIRMATION,
    ACTION_REQUIRED
}

data class UpcomingDisplayItem(
    val originalId: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: Date = Date(),
    val type: TransactionType = TransactionType.EXPENSE,
    val isRecurring: Boolean = false,
    val confirmationState: ConfirmationState = ConfirmationState.NONE,
    val isPostponed: Boolean = false,
    val accountName: String? = null
)

data class Transaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: Date? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val accountId: String = "",
    val category: Category = Category.OTHER,
    val recurringItemId: String? = null,
    val transferId: String? = null,
    val frequency: Frequency? = null,
    val wasPaidEarly: Boolean = false,
    var skippedUntil: Date? = null,
    val isBill: Boolean = false
)

data class RecurringItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val type: TransactionType = TransactionType.EXPENSE,
    val frequency: Frequency = Frequency.MONTHLY,
    var nextDate: Date? = null,
    val category: Category = Category.OTHER,
    val isFluctuating: Boolean = false,
    val skippedUntil: Date? = null,
    // --- FIX: Added skippedDates field ---
    val skippedDates: List<Date> = emptyList(),
    val isPostponed: Boolean = false,
    val accountId: String = ""
)

enum class TransactionType { INCOME, EXPENSE }
enum class Frequency { WEEKLY, BIWEEKLY, MONTHLY, YEARLY }

enum class Category(val displayName: String) {
    PAYCHECK("Paycheck"),
    HOUSING("Housing"),
    TRANSPORTATION("Transportation"),
    FOOD("Food"),
    UTILITIES("Utilities"),
    INSURANCE("Insurance"),
    HEALTHCARE("Healthcare"),
    SAVINGS_TRANSFER("Savings & Transfer"),
    PERSONAL("Personal"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Other")
}