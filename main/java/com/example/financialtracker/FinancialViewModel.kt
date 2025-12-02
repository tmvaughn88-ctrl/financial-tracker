// FinancialViewModel.kt

package com.example.financialtracker

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat // Import SimpleDateFormat for logging
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.O)
class FinancialViewModel(application: Application) : AndroidViewModel(application) {
    val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private var trackersListener: ListenerRegistration? = null
    private var hasInitialized = false

    private val activeListeners = mutableListOf<ListenerRegistration>()

    var isUserLoggedIn by mutableStateOf(auth.currentUser != null)
    var authError by mutableStateOf<String?>(null)
    var passwordResetStatus by mutableStateOf<String?>(null) // For password reset

    var isLoadingTrackers by mutableStateOf(true)
    var userTrackersList by mutableStateOf<List<Tracker>>(emptyList())
    val hasTrackers: Boolean get() = userTrackersList.isNotEmpty()
    var activeTracker by mutableStateOf<Tracker?>(null)
    var joinTrackerError by mutableStateOf<String?>(null)
    var joinRequestStatus by mutableStateOf<String?>(null)
    var navigatingToSelection by mutableStateOf(false)
    var joinRequests by mutableStateOf<List<JoinRequest>>(emptyList())

    var accounts by mutableStateOf<List<Account>>(emptyList())

    private val _transactions = mutableStateOf<List<Transaction>>(emptyList())
    private val _recurringItems = mutableStateOf<List<RecurringItem>>(emptyList())
    private val _allProjectedTransactions = mutableStateOf<List<Transaction>>(emptyList())

    private val _allUpcomingItems = mutableStateOf<List<UpcomingDisplayItem>>(emptyList())
    val allUpcomingItems: List<UpcomingDisplayItem> get() = _allUpcomingItems.value
    val upcomingItems: List<UpcomingDisplayItem> get() = _allUpcomingItems.value.take(5)

    private val _accountHistoryTransactions = mutableStateOf<List<Transaction>>(emptyList())
    val accountHistoryTransactions: List<Transaction> get() = _accountHistoryTransactions.value

    var itemToEdit by mutableStateOf<Any?>(null)
    var selectedRecurringItem by mutableStateOf<RecurringItem?>(null)
    var upcomingItemForAction by mutableStateOf<UpcomingDisplayItem?>(null)
    var itemToPostpone by mutableStateOf<RecurringItem?>(null)
    var itemToSplit by mutableStateOf<UpcomingDisplayItem?>(null)
    var itemToDelete by mutableStateOf<Any?>(null) // For delete confirmation

    var currentPage by mutableStateOf(Page.DASHBOARD)
    var selectedAccountIdForHistory by mutableStateOf<String?>(null)
    var selectedCategoryFilter by mutableStateOf<Category?>(null)
    var searchQuery by mutableStateOf("") // For search

    var startDateFilter by mutableStateOf<Date?>(null)
    var endDateFilter by mutableStateOf<Date?>(null)

    // --- Future Scope State ---
    var futureScopeTransactions by mutableStateOf<List<Transaction>>(emptyList())
    var futureScopeStartDate by mutableStateOf<Date?>(null)
    var futureScopeEndDate by mutableStateOf<Date?>(null)
    var estimatedTotalFutureBalance by mutableStateOf<Double?>(null)
    var estimatedAccountEndBalances by mutableStateOf<Map<String, Double>>(emptyMap())

    // --- Calendar Scope State ---
    var selectedCalendarDate by mutableStateOf(LocalDate.now())
    var transactionsForSelectedCalendarDate by mutableStateOf<List<Transaction>>(emptyList())
    // Stores dates with *future* or *today's* transactions (projected included)
    var upcomingTransactionsByDate by mutableStateOf<Map<LocalDate, List<Transaction>>>(emptyMap())
    // Stores dates with *past* transactions (actual only)
    var pastTransactionsByDate by mutableStateOf<Map<LocalDate, List<Transaction>>>(emptyMap())

    var projectedTotalBalanceForSelectedDate by mutableStateOf<Double?>(null)
    var projectedAccountBalancesForSelectedDate by mutableStateOf<Map<String, Double>>(emptyMap())


    val transactions: List<Transaction>
        get() {
            val now = Date()
            // Includes transactions up to the current moment
            return _transactions.value
                .filter { it.date != null && !it.date.after(now) }
                .sortedByDescending { it.date }
        }
    val recurringItems: List<RecurringItem> get() = _recurringItems.value

    // *** Data Class for Charting ***
    data class PieChartSegment(
        val category: Category,
        val amount: Double,
        val color: Color
    )
    // ******************************

    // Calculates the *current* balance based on transactions up to this exact moment.
    fun getBalanceForAccount(accountId: String): Double {
        return _transactions.value
            .filter { it.accountId == accountId && it.date != null && !it.date.after(Date()) } // Use Date() for current moment
            .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
    }

    // Calculates the *current* total balance across all accounts.
    val totalCurrentBalance: Double
        get() = accounts.sumOf { getBalanceForAccount(it.id) }

    val totalCheckingBalance: Double
        get() = accounts
            .filter { it.type.equals("Checking", ignoreCase = true) }
            .sumOf { getBalanceForAccount(it.id) }

    val totalSavingsBalance: Double
        get() = accounts
            .filter { !it.type.equals("Checking", ignoreCase = true) }
            .sumOf { getBalanceForAccount(it.id) }

    val isCreator: Boolean
        get() = auth.currentUser?.uid == activeTracker?.creatorId

    // --- START: Budgeting Helper Functions ---
    private fun getStartOfCurrentMonth(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun getStartOfCurrentWeek(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
    // --- END: Budgeting Helper Functions ---


    // --- START: Budgeting Computed Properties ---
    private val budgetTransactions: List<Transaction>
        get() = _transactions.value
            .filter {
                it.type == TransactionType.EXPENSE &&
                        it.transferId == null && // Exclude transfers
                        it.recurringItemId == null && // Exclude recurring bills
                        !it.isBill // Exclude one-off bills
            }

    val totalMonthlySpending: Double
        get() {
            val startOfMonth = getStartOfCurrentMonth()
            return budgetTransactions
                .filter { it.date != null && !it.date.before(startOfMonth) }
                .sumOf { it.amount }
        }

    val totalWeeklySpending: Double
        get() {
            val startOfWeek = getStartOfCurrentWeek()
            return budgetTransactions
                .filter { it.date != null && !it.date.before(startOfWeek) }
                .sumOf { it.amount }
        }

    val monthlySpendingByCategory: Map<Category, Double>
        get() {
            val startOfMonth = getStartOfCurrentMonth()

            // Collect actual spending
            val actualSpending = budgetTransactions
                .filter { it.date != null && !it.date.before(startOfMonth) }
                .groupBy { it.category }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }

            // Combine with all categories, defaulting to 0.0 if no spending
            return Category.values()
                .associateWith { category -> actualSpending[category] ?: 0.0 }
                .filter { it.value >= 0 } // Safety filter
                .toSortedMap(compareBy { it.displayName })
        }

    val weeklySpendingByCategory: Map<Category, Double>
        get() {
            val startOfWeek = getStartOfCurrentWeek()
            return budgetTransactions
                .filter { it.date != null && !it.date.before(startOfWeek) }
                .groupBy { it.category }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .filter { it.value > 0 } // This filter is okay for the weekly list as it's not directly for charting
                .toSortedMap(compareBy { it.displayName }) // Sort by category name
        }

    private val categoryColors = mapOf(
        Category.FOOD to Color(0xFFFF6384),        // Pink
        Category.HOUSING to Color(0xFF36A2EB),     // Blue
        Category.TRANSPORTATION to Color(0xFFFFCE56), // Yellow
        Category.UTILITIES to Color(0xFF4BC0C0),    // Teal
        Category.INSURANCE to Color(0xFF9966FF),    // Purple
        Category.HEALTHCARE to Color(0xFFFF9F40),   // Orange
        Category.PERSONAL to Color(0xFF2E8B57),    // Sea Green
        Category.ENTERTAINMENT to Color(0xFFC9CB3E), // Olive
        Category.OTHER to Color(0xFFC0C0C0)        // Gray
    )

    val monthlySpendingSegments: List<PieChartSegment>
        get() {
            val totalSpending = monthlySpendingByCategory.values.sum()
            if (totalSpending == 0.0) return emptyList()

            return monthlySpendingByCategory.map { (category, amount) ->
                PieChartSegment(
                    category = category,
                    amount = amount,
                    color = categoryColors[category] ?: Color.Gray
                )
            }.sortedByDescending { it.amount }
        }

    // --- "Available to Budget" Calculations ---
    private fun getNormalizedRecurringAmount(item: RecurringItem): Double {
        val amount = if (item.type == TransactionType.INCOME) item.amount else -item.amount
        return when (item.frequency) {
            Frequency.WEEKLY -> amount * 4.33
            Frequency.BIWEEKLY -> amount * 2.167
            Frequency.MONTHLY -> amount
            Frequency.YEARLY -> amount / 12
        }
    }

    val maxAvailableMonthlyBudget: Double
        get() = _recurringItems.value.sumOf { getNormalizedRecurringAmount(it) }

    val maxAvailableWeeklyBudget: Double
        get() = maxAvailableMonthlyBudget / 4.33 // Convert monthly average to weekly

    // --- END: Budgeting Computed Properties ---


    init {
        // Persistence is now ENABLED by default
        // by removing the code that disabled it.
        initializeListeners()
    }

    fun initializeListeners() {
        if (hasInitialized) return
        hasInitialized = true

        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            isUserLoggedIn = user != null

            if (user != null) {
                db.enableNetwork().addOnCompleteListener {
                    // This attaches the trackersListener ONCE on login.
                    attachTrackersListener(user.uid)
                }
            } else {
                // User is logged out. Clear ALL state and listeners.
                isLoadingTrackers = true
                activeTracker = null
                userTrackersList = emptyList()
                clearDataState() // Call new function to clear data

                // Detach ALL listeners
                trackersListener?.remove()
                trackersListener = null
                detachListeners() // This will clear activeListeners
            }
        }
    }

    private fun attachTrackersListener(userId: String) {
        if (trackersListener != null) {
            Log.d("FinancialViewModel", "Trackers listener is already active.")
            return
        }

        isLoadingTrackers = true
        trackersListener = db.collection("trackers").whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoadingTrackers = false
                    Log.w("FinancialViewModel", "Trackers listener failed.", e)
                    return@addSnapshotListener
                }
                try {
                    userTrackersList = snapshot?.toObjects(Tracker::class.java) ?: emptyList()
                    Log.d("FinancialViewModel", "Trackers query returned ${userTrackersList.size} trackers.")
                    if (userTrackersList.isEmpty()) {
                        Log.w("FinancialViewModel", "Trackers list is empty. Query for user ID '$userId' found no matches.")
                    }
                    if (activeTracker != null && userTrackersList.none { it.id == activeTracker?.id }) {
                        activeTracker = null
                        detachListeners() // Detach data listeners if active tracker is gone
                    }
                    // --- START: Update Active Tracker with Budget ---
                    activeTracker?.id?.let { currentId ->
                        activeTracker = userTrackersList.find { it.id == currentId }
                    }
                    // --- END: Update Active Tracker with Budget ---
                    isLoadingTrackers = false
                } catch (ex: Exception) {
                    Log.e("FinancialViewModel", "Error processing trackers snapshot", ex)
                    isLoadingTrackers = false
                }
            }
    }

    fun selectActiveTracker(tracker: Tracker) {
        navigatingToSelection = false
        activeTracker = tracker
        listenForDataChanges(tracker.id)
    }

    private fun listenForDataChanges(trackerId: String) {
        Log.d("FinancialViewModel", "Attaching data listeners for tracker: $trackerId")
        detachListeners() // Clear old data listeners first

        val accountsListener = db.collection("trackers").document(trackerId).collection("accounts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FinancialViewModel", "Accounts listener failed.", e)
                    return@addSnapshotListener
                }
                try {
                    accounts = snapshot?.toObjects(Account::class.java)?.sortedBy { it.name } ?: emptyList()
                    recalculateDerivedLists()
                } catch (ex: Exception) {
                    Log.e("FinancialViewModel", "Error processing ACCOUNT snapshot", ex)
                }
            }
        activeListeners.add(accountsListener)

        val transactionsListener = db.collection("trackers").document(trackerId).collection("transactions")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FinancialViewModel", "Transactions listener failed.", e)
                    return@addSnapshotListener
                }
                try {
                    _transactions.value = snapshot?.toObjects(Transaction::class.java) ?: emptyList()
                    recalculateDerivedLists()
                } catch (ex: Exception) {
                    Log.e("FinancialViewModel", "Error processing TRANSACTION snapshot", ex)
                }
            }
        activeListeners.add(transactionsListener)

        val recurringItemsListener = db.collection("trackers").document(trackerId).collection("recurringItems")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FinancialViewModel", "Recurring Items listener failed.", e)
                    return@addSnapshotListener
                }
                try {
                    _recurringItems.value = snapshot?.toObjects(RecurringItem::class.java) ?: emptyList()
                    recalculateDerivedLists()
                } catch (ex: Exception) {
                    Log.e("FinancialViewModel", "Error processing RECURRING ITEMS snapshot", ex)
                }
            }
        activeListeners.add(recurringItemsListener)

        if (isCreator) {
            val joinRequestListener = db.collection("trackers").document(trackerId).collection("joinRequests")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.w("FinancialViewModel", "Join requests listener failed.", e)
                        return@addSnapshotListener
                    }
                    try {
                        joinRequests = snapshot?.toObjects(JoinRequest::class.java) ?: emptyList()
                    } catch (ex: Exception) {
                        Log.e("FinancialViewModel", "Error processing JOIN REQUESTS snapshot", ex)
                    }
                }
            activeListeners.add(joinRequestListener)
        }
    }

    // Called from MainActivity's onResume
    fun attachDataListeners() {
        // If we have an active tracker and no active listeners (from being paused),
        // re-attach the data listeners.
        if (activeTracker?.id != null && activeListeners.isEmpty()) {
            Log.d("FinancialViewModel", "Re-attaching data listeners (onResume).")
            listenForDataChanges(activeTracker!!.id)
        }
    }

    // Called from MainActivity's onPause
    fun detachListeners() {
        // This function now ONLY detaches the data-heavy listeners
        // (accounts, transactions, etc.). It leaves trackersListener alone.
        Log.d("FinancialViewModel", "Detaching data listeners (onPause or switch).")
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    // Clears all data state when switching trackers or logging out
    private fun clearDataState() {
        _transactions.value = emptyList()
        _recurringItems.value = emptyList()
        _allProjectedTransactions.value = emptyList()
        accounts = emptyList()
        joinRequests = emptyList()
    }


    // Generates a list of all known and projected future transactions.
    private fun processFutureTransactions() {
        val now = Date()
        val startOfTodayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = startOfTodayCalendar.time

        val allProjectedTransactions = mutableListOf<Transaction>()
        // Include ALL actual transactions (historical and today) that haven't been skipped indefinitely
        allProjectedTransactions.addAll(_transactions.value.filter { it.date != null && it.recurringItemId == null && (it.skippedUntil == null || it.skippedUntil?.after(Date()) == false) })


        val projectionEndDate = Calendar.getInstance().apply { add(Calendar.YEAR, 5) }.time
        _recurringItems.value.distinctBy { it.id }.forEach { item ->
            item.nextDate?.let { startDate ->
                // Ensure the start date for projection is not before today if the original nextDate is in the past
                val actualStartDate = if (startDate.before(startOfToday)) {
                    val tempCal = Calendar.getInstance().apply { time = startDate }
                    while (tempCal.time.before(startOfToday)) {
                        calculateNextDate(tempCal, item.frequency)
                    }
                    tempCal.time
                } else {
                    startDate
                }

                val calendar = Calendar.getInstance().apply { time = actualStartDate }

                // Generate future instances of recurring items
                while (calendar.time.before(projectionEndDate)) {
                    val currentDate = calendar.time

                    // *** CHECK 1: Is this specific date in the skippedDates list? ***
                    // We check if the Year and Day of Year match any date in the skipped list
                    val isSkippedSpecific = item.skippedDates.any { skipped ->
                        val sCal = Calendar.getInstance().apply { time = skipped }
                        val cCal = Calendar.getInstance().apply { time = currentDate }
                        sCal.get(Calendar.YEAR) == cCal.get(Calendar.YEAR) &&
                                sCal.get(Calendar.DAY_OF_YEAR) == cCal.get(Calendar.DAY_OF_YEAR)
                    }

                    // *** CHECK 2: Is this before the legacy skippedUntil date? ***
                    val isSkippedUntil = item.skippedUntil != null && currentDate.before(item.skippedUntil)

                    // Only add if NOT skipped
                    if (!isSkippedSpecific && !isSkippedUntil) {
                        allProjectedTransactions.add(
                            Transaction(
                                id = "${item.id}-${calendar.timeInMillis}", // Unique ID for projected instance
                                description = item.description, amount = item.amount, date = calendar.time,
                                type = item.type, accountId = item.accountId, category = item.category,
                                recurringItemId = item.id, frequency = item.frequency,
                                isBill = true // All recurring items are bills
                            )
                        )
                    }
                    calculateNextDate(calendar, item.frequency) // Move to the next date regardless of skip status
                }
            }
        }
        _allProjectedTransactions.value = allProjectedTransactions.sortedBy { it.date }

        // Filter for Future Scope List (transactions strictly after now)
        futureScopeTransactions = if (futureScopeStartDate != null && futureScopeEndDate != null) {
            _allProjectedTransactions.value.filter {
                it.date != null && !it.date.before(futureScopeStartDate) && !it.date.after(futureScopeEndDate)
            }
        } else {
            // Updated: Future scope should strictly be AFTER the current moment
            _allProjectedTransactions.value.filter { it.date != null && it.date.after(now) }
        }

        // Calculate estimated balances for Future Scope List range
        estimatedTotalFutureBalance = null
        estimatedAccountEndBalances = emptyMap()
        if (futureScopeStartDate != null && futureScopeEndDate != null) {
            estimatedAccountEndBalances = getProjectedAccountBalancesForDate(
                futureScopeEndDate!!.toInstant().atZone(TimeZone.getDefault().toZoneId()).toLocalDate()
            )
            estimatedTotalFutureBalance = estimatedAccountEndBalances.values.sum()
        }

        // Group transactions by date for the Calendar view
        // Upcoming includes today and future (projected included)
        upcomingTransactionsByDate = _allProjectedTransactions.value
            .filter { it.date != null && !it.date.before(startOfToday) } // Show from today onwards
            .groupBy {
                it.date!!.toInstant().atZone(TimeZone.getDefault().toZoneId()).toLocalDate()
            }

        // Past includes only actual transactions before today
        pastTransactionsByDate = _transactions.value // Use only actual transactions
            .filter { it.date != null && it.date.before(startOfToday) } // Only before today
            .groupBy {
                it.date!!.toInstant().atZone(TimeZone.getDefault().toZoneId()).toLocalDate()
            }
    }


    // Recalculates lists derived from transactions and recurring items
    private fun recalculateDerivedLists() {
        val todayCalendar = Calendar.getInstance()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        // Regenerate all projections based on the latest _transactions and _recurringItems
        processFutureTransactions() // This now builds _allProjectedTransactions, upcomingTransactionsByDate, and pastTransactionsByDate

        // *** FIX: Define 'now' for current moment check ***
        val now = Date()
        // **************************************************

        // Upcoming Items for Dashboard (next 5 items from startOfToday onwards, excluding paid early and skipped)
        val upcomingGenerated = _allProjectedTransactions.value // Use the comprehensive projected list
            .filter { transaction ->
                // Basic cleanup filters
                if (transaction.date == null || transaction.wasPaidEarly || (transaction.skippedUntil != null && transaction.date.after(transaction.skippedUntil))) {
                    return@filter false
                }

                // --- CORE LOGIC: Exclude transactions that are already "done" and not a projection/bill ---
                val isSimpleTransaction = transaction.recurringItemId == null && !transaction.isBill && transaction.transferId == null

                if (isSimpleTransaction) {
                    // Simple transactions must be strictly in the future to be "Upcoming"
                    return@filter transaction.date.after(now)
                }

                // Recurring items and bills (projected or actual) are included if their date is today or later
                return@filter !transaction.date.before(startOfToday)
            }
            .mapNotNull { transaction ->
                val originalRecurring = if (transaction.recurringItemId != null) {
                    _recurringItems.value.find { it.id == transaction.recurringItemId }
                } else null

                // Skip this projection if it represents a recurring item whose *actual* nextDate is later
                // This prevents showing projections for dates already handled by the recurring item's actual nextDate advancement
                if (originalRecurring != null && originalRecurring.nextDate != null && transaction.date!!.before(originalRecurring.nextDate)) {
                    // Log.d("UpcomingCalc", "Skipping projection ${transaction.description} on ${transaction.date} because original next date is ${originalRecurring.nextDate}")
                    return@mapNotNull null
                }


                val confirmationState = if (originalRecurring != null && originalRecurring.isFluctuating) {
                    val itemCalendar = Calendar.getInstance().apply { time = transaction.date!! }
                    val daysUntilDue = ((itemCalendar.timeInMillis - todayCalendar.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                    when {
                        daysUntilDue <= 14 -> ConfirmationState.ACTION_REQUIRED
                        daysUntilDue <= 28 -> ConfirmationState.NEEDS_CONFIRMATION
                        else -> ConfirmationState.NONE
                    }
                } else {
                    ConfirmationState.NONE
                }

                val accountName = accounts.find { it.id == transaction.accountId }?.name
                val displayId = originalRecurring?.id ?: transaction.id
                val isPostponed = originalRecurring?.isPostponed ?: false


                UpcomingDisplayItem(
                    originalId = displayId,
                    description = transaction.description,
                    amount = transaction.amount,
                    date = transaction.date!!,
                    type = transaction.type,
                    isRecurring = transaction.recurringItemId != null,
                    confirmationState = confirmationState,
                    isPostponed = isPostponed,
                    accountName = accountName
                )
            }
            // Ensure distinctness based on the original ID and the specific projected date
            .distinctBy { Pair(it.originalId, it.date.time) }
            .sortedBy { it.date }

        _allUpcomingItems.value = upcomingGenerated

        // Account History (filtered past transactions)
        _accountHistoryTransactions.value = transactions
            .filter { it.accountId == selectedAccountIdForHistory }
            // --- START: Search Filter ---
            .filter {
                searchQuery.isBlank() || it.description.contains(searchQuery, ignoreCase = true)
            }
            // --- END: Search Filter ---
            .filter { selectedCategoryFilter == null || it.category == selectedCategoryFilter }
            .filter {
                val startDate = startDateFilter
                val endDate = endDateFilter
                if (startDate != null && endDate != null) {
                    it.date != null && !it.date.before(startDate) && !it.date.after(endDate)
                } else {
                    true
                }
            }


        // Refresh data for the currently selected calendar date AFTER projections are updated
        onCalendarDateSelected(selectedCalendarDate)
    }

    // --- Add/Delete/Auth functions ---

    // --- START OF CHANGE: Fix all `set` operations ---

    fun addAccount(name: String, type: String) {
        val trackerId = activeTracker?.id ?: return
        val docRef = db.collection("trackers").document(trackerId).collection("accounts").document()
        // Use a map to avoid writing the 'id' field
        val newAccountData = mapOf(
            "name" to name,
            "type" to type
        )
        docRef.set(newAccountData)
    }

    fun deleteAccount(accountId: String) {
        val trackerId = activeTracker?.id ?: return
        db.collection("trackers").document(trackerId).collection("transactions")
            .whereEqualTo("accountId", accountId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                querySnapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener {
                    db.collection("trackers").document(trackerId).collection("accounts")
                        .document(accountId).delete()
                    // No explicit recalculate, listener will handle
                }
            }
    }

    fun createNewTracker(trackerName: String) {
        val user = auth.currentUser ?: return
        val trackerId = UUID.randomUUID().toString() // This will be the document ID

        // Use a map to avoid writing the 'id' field
        val trackerData = mapOf(
            "name" to trackerName,
            "members" to listOf(user.uid),
            "creatorId" to user.uid,
            "monthlyBudget" to 0.0,
            "weeklyBudget" to 0.0
        )

        val trackerRef = db.collection("trackers").document(trackerId)

        trackerRef.set(trackerData)
            .addOnSuccessListener {
                // Create the local Tracker object for selection
                val newTracker = Tracker(
                    id = trackerId,
                    name = trackerName,
                    members = listOf(user.uid),
                    creatorId = user.uid,
                    monthlyBudget = 0.0,
                    weeklyBudget = 0.0
                )

                // Add default accounts (also using maps)
                val accountsCollection = db.collection("trackers").document(trackerId).collection("accounts")
                val checkingRef = accountsCollection.document()
                val checkingData = mapOf("name" to "Checking", "type" to "Checking")
                val savingsRef = accountsCollection.document()
                val savingsData = mapOf("name" to "Savings", "type" to "Savings")

                val batch = db.batch()
                batch.set(checkingRef, checkingData)
                batch.set(savingsRef, savingsData)
                batch.commit().addOnSuccessListener {
                    selectActiveTracker(newTracker)
                }
            }
    }

    fun signIn(email: String, pass: String) {
        authError = null
        passwordResetStatus = null // Clear reset status on login attempt
        if (email.isBlank() || pass.isBlank()) {
            authError = "Email and password cannot be empty."
            return
        }
        auth.signInWithEmailAndPassword(email, pass)
            .addOnFailureListener {
                authError = it.message ?: "An unknown login error occurred."
            }
    }

    fun signUp(email: String, pass: String) {
        authError = null
        passwordResetStatus = null // Clear reset status on sign-up attempt
        if (email.isBlank() || pass.isBlank()) {
            authError = "Email and password cannot be empty."
            return
        }
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnFailureListener {
                authError = it.message ?: "An unknown sign-up error occurred."
            }
    }

    // Function to handle password reset request
    fun sendPasswordResetEmail(email: String) {
        authError = null
        passwordResetStatus = null
        if (email.isBlank()) {
            passwordResetStatus = "Please enter your email address."
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    passwordResetStatus = "Password reset email sent to $email."
                } else {
                    passwordResetStatus = task.exception?.message ?: "Failed to send reset email."
                }
            }
    }


    fun requestToJoinTracker(trackerId: String) {
        val user = auth.currentUser ?: return
        joinTrackerError = null
        joinRequestStatus = null

        val trackerRef = db.collection("trackers").document(trackerId.trim())
        trackerRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val requestRef = trackerRef.collection("joinRequests").document() // Create ref
                    // Use a map to avoid writing the 'id' field
                    val requestData = mapOf(
                        "trackerId" to trackerId.trim(),
                        "requestingUserId" to user.uid,
                        "requestingUserEmail" to (user.email ?: "No Email Provided")
                    )
                    requestRef.set(requestData)
                        .addOnSuccessListener {
                            joinRequestStatus = "Request sent successfully!"
                        }
                        .addOnFailureListener {
                            joinTrackerError = "Failed to send request. Try again."
                        }
                } else {
                    joinTrackerError = "Tracker with that ID does not exist."
                }
            }
            .addOnFailureListener {
                joinTrackerError = "Could not connect to verify tracker."
            }
    }

    fun acceptJoinRequest(request: JoinRequest) {
        val trackerRef = db.collection("trackers").document(request.trackerId)
        db.runTransaction { transaction ->
            transaction.update(trackerRef, "members", FieldValue.arrayUnion(request.requestingUserId))
            transaction.delete(trackerRef.collection("joinRequests").document(request.id))
            null
        } // Listener will handle UI update
    }

    fun denyJoinRequest(request: JoinRequest) {
        db.collection("trackers").document(request.trackerId)
            .collection("joinRequests").document(request.id).delete()
        // Listener will handle UI update
    }

    fun signOut() {
        auth.signOut()
    }

    fun deleteTracker() {
        if (!isCreator) return
        val trackerToDelete = activeTracker ?: return
        db.collection("trackers").document(trackerToDelete.id).delete()
            .addOnSuccessListener { activeTracker = null } // Listener will update tracker list
    }


    // --- Transaction/Recurring Item modification functions ---
    fun addTransaction(description: String, amount: Double, type: TransactionType, accountId: String, date: Date, category: Category, isBill: Boolean) {
        val trackerId = activeTracker?.id ?: return
        val userId = auth.currentUser?.uid ?: return

        val docRef = db.collection("trackers").document(trackerId).collection("transactions").document()
        // Use a map to avoid writing the 'id' field
        val newTransactionData = mapOf(
            "userId" to userId,
            "description" to description,
            "amount" to amount,
            "date" to date,
            "type" to type,
            "accountId" to accountId,
            "category" to category,
            "recurringItemId" to null,
            "transferId" to null,
            "frequency" to null,
            "wasPaidEarly" to false,
            "skippedUntil" to null,
            "isBill" to isBill
        )
        docRef.set(newTransactionData)
    }

    fun addTransfer(description: String, amount: Double, fromAccountId: String, toAccountId: String, date: Date) {
        val trackerId = activeTracker?.id ?: return
        val userId = auth.currentUser?.uid ?: return
        val transferId = UUID.randomUUID().toString()
        val batch = db.batch()

        // Create expense doc
        val expenseRef = db.collection("trackers").document(trackerId).collection("transactions").document()
        val expenseData = mapOf(
            "userId" to userId,
            "description" to description,
            "amount" to amount,
            "date" to date,
            "type" to TransactionType.EXPENSE,
            "accountId" to fromAccountId,
            "category" to Category.SAVINGS_TRANSFER,
            "transferId" to transferId,
            "recurringItemId" to null,
            "frequency" to null,
            "wasPaidEarly" to false,
            "skippedUntil" to null,
            "isBill" to false // Transfers are not bills
        )

        // Create income doc
        val incomeRef = db.collection("trackers").document(trackerId).collection("transactions").document()
        val incomeData = mapOf(
            "userId" to userId,
            "description" to description,
            "amount" to amount,
            "date" to date,
            "type" to TransactionType.INCOME,
            "accountId" to toAccountId,
            "category" to Category.SAVINGS_TRANSFER,
            "transferId" to transferId,
            "recurringItemId" to null,
            "frequency" to null,
            "wasPaidEarly" to false,
            "skippedUntil" to null,
            "isBill" to false // Income is not a bill
        )

        batch.set(expenseRef, expenseData)
        batch.set(incomeRef, incomeData)

        batch.commit()
    }

    fun updateTransaction(transaction: Transaction) {
        val trackerId = activeTracker?.id ?: return

        // Check if this is a projected transaction (ID contains a hyphen and timestamp)
        val isProjectedInstance = transaction.recurringItemId != null && transaction.id.contains("-")

        if (isProjectedInstance) {
            Log.d("ViewModel", "Updating projected instance. Creating new one-off.")
            val originalItem = _recurringItems.value.find { it.id == transaction.recurringItemId }
            if (originalItem != null) {
                // Add the modified one-off transaction
                addTransaction( // This will create a new doc with a new ID
                    description = transaction.description,
                    amount = transaction.amount,
                    type = transaction.type,
                    accountId = transaction.accountId, // This will have the new account ID from the dialog
                    date = transaction.date!!,
                    category = transaction.category,
                    isBill = transaction.isBill // Pass the isBill flag
                )

                // *** FIX: Use skippedDates to hide this specific projection ***
                val newSkippedList = originalItem.skippedDates.toMutableList()
                newSkippedList.add(transaction.date)
                updateRecurringItem(originalItem.copy(skippedDates = newSkippedList))
            }
        } else {
            // This is a real, one-off, or historical transaction. Update it directly.
            Log.d("ViewModel", "Updating real transaction with ID: ${transaction.id}")
            // When updating, we must use a map to avoid writing the 'id' field
            val transactionData = mapOf(
                "userId" to transaction.userId,
                "description" to transaction.description,
                "amount" to transaction.amount,
                "date" to transaction.date,
                "type" to transaction.type,
                "accountId" to transaction.accountId,
                "category" to transaction.category,
                "recurringItemId" to transaction.recurringItemId,
                "transferId" to transaction.transferId,
                "frequency" to transaction.frequency,
                "wasPaidEarly" to transaction.wasPaidEarly,
                "skippedUntil" to transaction.skippedUntil,
                "isBill" to transaction.isBill
            )
            db.collection("trackers").document(trackerId).collection("transactions")
                .document(transaction.id).set(transactionData) // Use set with map
        }
    }


    fun removeTransaction(transaction: Transaction) {
        val trackerId = activeTracker?.id ?: return
        db.collection("trackers").document(trackerId).collection("transactions").document(transaction.id).delete()
    }

    // --- START: MODIFIED FUNCTION ---
    fun updateRecurringItem(item: RecurringItem) {
        val trackerId = activeTracker?.id ?: return

        // This variable will be true if we are CREATING a new item
        val isNewItem = item.id.isEmpty()

        val docRef = if (isNewItem) {
            db.collection("trackers").document(trackerId).collection("recurringItems").document()
        } else {
            db.collection("trackers").document(trackerId).collection("recurringItems").document(item.id)
        }

        // Use a map to avoid writing the 'id' field
        val itemData = mapOf(
            "userId" to item.userId,
            "description" to item.description,
            "amount" to item.amount,
            "type" to item.type,
            "frequency" to item.frequency,
            "nextDate" to item.nextDate,
            "category" to item.category,
            "isFluctuating" to item.isFluctuating,
            "skippedUntil" to item.skippedUntil,
            "skippedDates" to item.skippedDates, // *** ADDED: persist skipped dates list ***
            "isPostponed" to item.isPostponed,
            "accountId" to item.accountId
        )
        docRef.set(itemData)

        // If this is a NEW item (not an update) and its date is tomorrow,
        // schedule an immediate one-time check.
        if (isNewItem && item.nextDate != null) {
            val itemCal = Calendar.getInstance().apply { time = item.nextDate!! }
            val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

            val isTomorrow = itemCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
                    itemCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)

            if (isTomorrow) {
                Log.d("FinancialViewModel", "New item added for tomorrow. Scheduling one-time notification check.")
                NotificationWorker.scheduleOneTimeCheck(getApplication())
            }
        }
    }
    // --- END: MODIFIED FUNCTION ---

    fun dismissUpcomingItem(item: UpcomingDisplayItem) {
        // Hiding a specific upcoming item now uses skippedDates if it's a projection
        val originalItem = _recurringItems.value.find { it.id == item.originalId }

        if (originalItem != null) {
            val newSkippedList = originalItem.skippedDates.toMutableList()
            newSkippedList.add(item.date)
            updateRecurringItem(originalItem.copy(skippedDates = newSkippedList))
        }
    }

    fun refreshUpcomingItems() = viewModelScope.launch {
        try {
            val trackerId = activeTracker?.id ?: return@launch
            val batch = db.batch()
            val recurringDocs = db.collection("trackers").document(trackerId)
                .collection("recurringItems").whereNotEqualTo("skippedUntil", null).get().await()
            recurringDocs.forEach { batch.update(it.reference, "skippedUntil", FieldValue.delete()) }
            val transactionDocs = db.collection("trackers").document(trackerId)
                .collection("transactions").whereNotEqualTo("skippedUntil", null).get().await()
            transactionDocs.forEach { batch.update(it.reference, "skippedUntil", FieldValue.delete()) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("FinancialViewModel", "Error refreshing items.", e)
        }
    }

    fun adjustBalance(accountId: String, correctBalance: Double) {
        val today = Date()
        val currentBalance = getBalanceForAccount(accountId)
        val difference = correctBalance - currentBalance
        if (difference != 0.0) {
            addTransaction(
                description = "Balance Adjustment", amount = abs(difference),
                type = if (difference > 0) TransactionType.INCOME else TransactionType.EXPENSE,
                accountId = accountId, date = today, category = Category.OTHER,
                isBill = true // Adjustments should not count towards budget
            )
        }
    }

    fun convertToRecurring(transaction: Transaction, frequency: Frequency, isFluctuating: Boolean) {
        transaction.date?.let { transactionDate ->
            val calendar = Calendar.getInstance().apply { time = transactionDate }
            val today = Calendar.getInstance()
            while (calendar.before(today)) {
                calculateNextDate(calendar, frequency)
            }
            val nextDate = calendar.time

            val trackerId = activeTracker?.id ?: return
            val recDocRef = db.collection("trackers").document(trackerId).collection("recurringItems").document() // Create ref

            // Use a map to avoid writing the 'id' field
            val newRecurringData = mapOf(
                "description" to transaction.description,
                "amount" to transaction.amount,
                "type" to transaction.type,
                "frequency" to frequency,
                "nextDate" to nextDate,
                "category" to transaction.category,
                "isFluctuating" to isFluctuating,
                "accountId" to transaction.accountId,
                "userId" to transaction.userId,
                "skippedUntil" to null,
                "isPostponed" to false
            )

            // Use a map to update the existing transaction
            val updatedTransactionData = mapOf(
                "userId" to transaction.userId,
                "description" to transaction.description,
                "amount" to transaction.amount,
                "date" to transaction.date,
                "type" to transaction.type,
                "accountId" to transaction.accountId,
                "category" to transaction.category,
                "recurringItemId" to recDocRef.id, // Link to the new recurring item
                "transferId" to transaction.transferId,
                "frequency" to frequency, // Add the frequency
                "wasPaidEarly" to transaction.wasPaidEarly,
                "skippedUntil" to transaction.skippedUntil,
                "isBill" to transaction.isBill
            )

            // Batch write
            val batch = db.batch()
            batch.set(recDocRef, newRecurringData) // Set the new recurring item
            // Update the existing transaction to link it
            batch.set(db.collection("trackers").document(trackerId).collection("transactions").document(transaction.id), updatedTransactionData)
            batch.commit()

        } ?: Log.e("FinancialViewModel", "Cannot convert transaction with null date to recurring.")
    }

    // --- START: FIX 2 ---
    fun deleteRecurringThisAndFuture(item: Any) {
        val trackerId = activeTracker?.id ?: return

        when (item) {
            is RecurringItem -> {
                // This is a template, just delete it
                db.collection("trackers").document(trackerId).collection("recurringItems").document(item.id).delete()
            }
            is Transaction -> {
                // This is an instance, find the template and delete that
                item.recurringItemId?.let { originalId ->
                    db.collection("trackers").document(trackerId).collection("recurringItems").document(originalId).delete()
                }
            }
        }
    }
    // --- END: FIX 2 ---

    // Skips the *next* date on the template
    fun skipNextRecurringOccurrence(item: RecurringItem) {
        item.nextDate?.let {
            val calendar = Calendar.getInstance().apply { time = it }
            // Must use updateRecurringItem to use the map-based logic
            updateRecurringItem(item.copy(nextDate = calculateNextDate(calendar, item.frequency).time))
        }
    }

    // Skips a *specific* projected date (Legacy helper, unused by new logic but kept for safety)
    fun skipProjectedRecurringOccurrence(item: RecurringItem, projectedDate: Date?) {
        if (projectedDate == null) return

        // Check if this projected date is the *current* nextDate
        val itemDateCal = Calendar.getInstance().apply { time = item.nextDate }
        val projDateCal = Calendar.getInstance().apply { time = projectedDate }

        if (itemDateCal.get(Calendar.YEAR) == projDateCal.get(Calendar.YEAR) &&
            itemDateCal.get(Calendar.DAY_OF_YEAR) == projDateCal.get(Calendar.DAY_OF_YEAR)) {
            // It is the current nextDate, so just advance the template
            skipNextRecurringOccurrence(item)
        } else {
            // It's a date further in the future. We use the new skippedDates logic now.
            val newSkippedList = item.skippedDates.toMutableList()
            newSkippedList.add(projectedDate)
            updateRecurringItem(item.copy(skippedDates = newSkippedList))
        }
    }

    // --- START: MODIFIED FUNCTION ---
    fun deleteSingleRecurringOccurrence(item: Any) {
        when (item) {
            is RecurringItem -> {
                // This is a template item (e.g., from Manage Accounts screen)
                // "Delete this instance" means "skip the next scheduled date."
                skipNextRecurringOccurrence(item)
            }
            is Transaction -> {
                if (item.recurringItemId != null && item.id.contains("-")) {
                    // This is a recurring *projected instance*. Find the template and add date to skippedDates.
                    val originalItem = _recurringItems.value.find { it.id == item.recurringItemId }
                    if (originalItem != null && item.date != null) {
                        val newSkippedList = originalItem.skippedDates.toMutableList()
                        newSkippedList.add(item.date)
                        updateRecurringItem(originalItem.copy(skippedDates = newSkippedList))
                    }
                } else {
                    // This is a one-off *transaction* (or paid recurring item). Just delete it.
                    removeTransaction(item)
                }
            }
        }
    }
    // --- END: MODIFIED FUNCTION ---

    fun payItemEarly(item: UpcomingDisplayItem) {
        val originalItem = _recurringItems.value.find { it.id == item.originalId } ?: return
        val accountId = if (originalItem.accountId.isNotBlank()) originalItem.accountId else accounts.firstOrNull()?.id ?: return
        val today = Date()

        // --- START OF CHANGE: Use map for new transaction ---
        val trackerId = activeTracker?.id ?: return
        val userId = auth.currentUser?.uid ?: return
        val docRef = db.collection("trackers").document(trackerId).collection("transactions").document()
        val newTransactionData = mapOf(
            "userId" to userId,
            "description" to originalItem.description,
            "amount" to originalItem.amount,
            "date" to today,
            "type" to originalItem.type,
            "accountId" to accountId,
            "category" to originalItem.category,
            "wasPaidEarly" to true, // Mark as paid early
            "recurringItemId" to originalItem.id, // Link it
            "transferId" to null,
            "frequency" to null,
            "skippedUntil" to null,
            "isBill" to true // All recurring items are bills
        )
        docRef.set(newTransactionData)
        // --- END OF CHANGE ---

        // Check if we are paying the *immediate* next bill or a future one
        val itemCal = Calendar.getInstance().apply { time = item.date }
        val nextCal = Calendar.getInstance().apply { time = originalItem.nextDate ?: Date() }

        val isSameDay = itemCal.get(Calendar.YEAR) == nextCal.get(Calendar.YEAR) &&
                itemCal.get(Calendar.DAY_OF_YEAR) == nextCal.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            // It was the next date, so advance the main schedule
            skipNextRecurringOccurrence(originalItem)
        } else {
            // It was a future date, so add it to the skippedDates list to hide the projection
            val newSkippedList = originalItem.skippedDates.toMutableList()
            newSkippedList.add(item.date)
            updateRecurringItem(originalItem.copy(skippedDates = newSkippedList))
        }

        clearUpcomingItemForAction()
    }

    fun postponeRecurringItem(item: RecurringItem) {
        itemToPostpone = item
    }

    fun confirmPostpone(newDate: Date) {
        itemToPostpone?.let { originalItem ->
            originalItem.nextDate?.let {
                updateRecurringItem(originalItem.copy(nextDate = calculateNextDate(Calendar.getInstance().apply { time = it }, originalItem.frequency).time))
                addTransaction(
                    description = originalItem.description, amount = originalItem.amount,
                    date = newDate, type = originalItem.type,
                    accountId = if (originalItem.accountId.isNotBlank()) originalItem.accountId else accounts.firstOrNull()?.id ?: "",
                    category = originalItem.category,
                    isBill = true // All recurring items are bills
                )
            }
        }
        itemToPostpone = null
    }

    fun markPostponedAsPaid(item: RecurringItem) {
        val today = Date()
        val accountId = if (item.accountId.isNotBlank()) item.accountId else accounts.firstOrNull()?.id ?: return
        addTransaction(
            description = item.description, amount = item.amount, date = today,
            type = item.type, accountId = accountId, category = item.category,
            isBill = true // All recurring items are bills
        )
        item.nextDate?.let {
            updateRecurringItem(item.copy(nextDate = calculateNextDate(Calendar.getInstance().apply { time = it }, item.frequency).time, isPostponed = false))
        } ?: updateRecurringItem(item.copy(isPostponed = false))
    }

    fun confirmFluctuatingAmount(item: RecurringItem, newAmount: Double) {
        val accountId = if (item.accountId.isNotBlank()) item.accountId else accounts.firstOrNull()?.id ?: return
        item.nextDate?.let { dateOfTransaction ->
            addTransaction(
                description = item.description, amount = newAmount, date = dateOfTransaction,
                type = item.type, accountId = accountId, category = item.category,
                isBill = true // All recurring items are bills
            )
            skipNextRecurringOccurrence(item)
        }
    }

    // *** START OF CRITICAL FIX: Add missing function for bill scanning ***
    fun confirmRecurringItemByScan(
        originalId: String,
        newAmount: Double,
        confirmedDate: Date
    ) {
        val originalItem = _recurringItems.value.find { it.id == originalId } ?: return
        val accountId = if (originalItem.accountId.isNotBlank()) originalItem.accountId else accounts.firstOrNull()?.id ?: return

        // 1. Create the new, confirmed transaction (similar to payItemEarly)
        val trackerId = activeTracker?.id ?: return
        val userId = auth.currentUser?.uid ?: return
        val docRef = db.collection("trackers").document(trackerId).collection("transactions").document()

        // Use a map to avoid writing the 'id' field
        val newTransactionData = mapOf(
            "userId" to userId,
            "description" to originalItem.description,
            "amount" to newAmount, // Use the new, scanned amount
            "date" to confirmedDate, // Use the new, scanned date
            "type" to originalItem.type,
            "accountId" to accountId,
            "category" to originalItem.category,
            "wasPaidEarly" to false,
            "recurringItemId" to originalItem.id,
            "transferId" to null,
            "frequency" to null,
            "skippedUntil" to null,
            "isBill" to true
        )
        docRef.set(newTransactionData)

        // 2. Advance the original recurring item's nextDate (similar to skipNextRecurringOccurrence)
        originalItem.nextDate?.let {
            val calendar = Calendar.getInstance().apply { time = it }
            val nextDate = calculateNextDate(calendar, originalItem.frequency).time

            // Update the recurring item, resetting the fluctuating status for the next cycle
            updateRecurringItem(originalItem.copy(
                nextDate = nextDate,
                isPostponed = false,
                skippedUntil = null // Clear any partial skip
            ))
        }
        clearUpcomingItemForAction()
    }
    // *** END OF CRITICAL FIX ***

    fun confirmSplitPayment(originalItem: UpcomingDisplayItem, numberOfPayments: Int, dates: List<Date>) {
        val originalRecurringItem = _recurringItems.value.find { it.id == originalItem.originalId }

        if (originalRecurringItem == null) return
        skipNextRecurringOccurrence(originalRecurringItem)

        val splitAmount = originalItem.amount / numberOfPayments
        val accountId = originalRecurringItem.accountId
        val category = originalRecurringItem.category
        val userId = auth.currentUser?.uid ?: ""

        val batch = db.batch()
        dates.forEachIndexed { index, date ->
            val docRef = db.collection("trackers").document(activeTracker!!.id).collection("transactions").document()
            // Use a map to avoid writing the 'id' field
            val splitTransactionData = mapOf(
                "description" to "${originalItem.description} (${index + 1} of $numberOfPayments)",
                "amount" to splitAmount,
                "type" to originalItem.type,
                "accountId" to accountId,
                "date" to date,
                "category" to category,
                "userId" to userId,
                "recurringItemId" to originalRecurringItem.id, // Link it
                "transferId" to null,
                "frequency" to null,
                "wasPaidEarly" to false,
                "skippedUntil" to null,
                "isBill" to true // All recurring items are bills
            )
            batch.set(docRef, splitTransactionData)
        }
        batch.commit()
        itemToSplit = null
    }
    // --- End Transaction/Recurring Item modification functions ---

    private fun calculateNextDate(startDate: Calendar, frequency: Frequency): Calendar {
        when (frequency) {
            Frequency.WEEKLY -> startDate.add(Calendar.WEEK_OF_YEAR, 1)
            Frequency.BIWEEKLY -> startDate.add(Calendar.WEEK_OF_YEAR, 2)
            Frequency.MONTHLY -> startDate.add(Calendar.MONTH, 1)
            Frequency.YEARLY -> startDate.add(Calendar.YEAR, 1)
        }
        return startDate
    }

    // --- Filter functions ---
    fun setDateFilter(startDate: Date?, endDate: Date?) {
        startDateFilter = startDate
        endDateFilter = endDate
        recalculateDerivedLists()
    }

    fun clearDateFilter() {
        startDateFilter = null
        endDateFilter = null
        recalculateDerivedLists()
    }

    fun setFutureScopeDateFilter(startDate: Date?, endDate: Date?) {
        futureScopeStartDate = startDate
        futureScopeEndDate = endDate
        processFutureTransactions() // This recalculates futureScopeTransactions based on the filter
    }

    fun setFutureScopeNext7Days() {
        val calStart = Calendar.getInstance()
        val calEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        setFutureScopeEndOfDay(calEnd)
        setFutureScopeDateFilter(calStart.time, calEnd.time)
    }

    fun setFutureScopeNext30Days() {
        val calStart = Calendar.getInstance()
        val calEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 30) }
        setFutureScopeEndOfDay(calEnd)
        setFutureScopeDateFilter(calStart.time, calEnd.time)
    }

    fun setFutureScopeEndOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
    }

    fun clearFutureScopeDateFilter() {
        futureScopeStartDate = null
        futureScopeEndDate = null
        processFutureTransactions() // Recalculate without date filter
    }
    // --- End Filter functions ---

    // --- Calendar Functions ---
    @RequiresApi(Build.VERSION_CODES.O)
    fun onCalendarDateSelected(date: LocalDate) {
        selectedCalendarDate = date
        // Filter transactions specifically for the selected date using the complete projected list
        transactionsForSelectedCalendarDate = _allProjectedTransactions.value.filter {
            it.date?.toInstant()?.atZone(TimeZone.getDefault().toZoneId())?.toLocalDate() == date
        }.sortedBy { it.date }
        // Get the calculated balances for the end of the selected date
        projectedAccountBalancesForSelectedDate = getProjectedAccountBalancesForDate(date)
        projectedTotalBalanceForSelectedDate = projectedAccountBalancesForSelectedDate.values.sum()
    }

    // Calculates the projected balance for each account for the given date.
    @RequiresApi(Build.VERSION_CODES.O)
    fun getProjectedAccountBalancesForDate(date: LocalDate): Map<String, Double> {
        val today = LocalDate.now()
        val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) // For detailed logging

        // If the selected date is today, return the *current* actual balances up to this moment.
        if (date == today) {
            Log.d("BalanceCalc", "Calculating for today (using current getBalanceForAccount)")
            return accounts.associate { account ->
                account.id to getBalanceForAccount(account.id) // Use the current balance function
            }
        }
        // If the selected date is in the past, calculate the balance as of the END of that day using only actual transactions.
        else if (date.isBefore(today)) {
            Log.d("BalanceCalc", "Calculating for past date: $date")
            val endOfDay = Calendar.getInstance().apply {
                set(Calendar.YEAR, date.year)
                set(Calendar.MONTH, date.monthValue - 1)
                set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time
            Log.d("BalanceCalc", "Past end of day: ${logDateFormat.format(endOfDay)}")

            return accounts.associate { account ->
                // Sum only ACTUAL transactions (_transactions) up to the end of that past day
                val pastTransactions = _transactions.value // Use _transactions, not _allProjectedTransactions
                    .filter { it.accountId == account.id && it.date != null && !it.date.after(endOfDay) }

                val balance = pastTransactions.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                Log.d("BalanceCalc Past [${account.name}]", "Final balance for $date: $balance")
                account.id to balance
            }
        }
        // If the date is in the future, calculate projected balance starting from the current balance.
        else { // date.isAfter(today)
            Log.d("BalanceCalc Future", "Calculating projection for future date: $date")
            val endOfDayFuture = Calendar.getInstance().apply {
                set(Calendar.YEAR, date.year)
                set(Calendar.MONTH, date.monthValue - 1)
                set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time
            // Log.d("BalanceCalc Future", "Future end of day: ${logDateFormat.format(endOfDayFuture)}")

            val now = Date() // Current moment
            // Log.d("BalanceCalc Future", "Current moment (now): ${logDateFormat.format(now)}")

            return accounts.associate { account ->
                // Start with the current balance
                val currentBalance = getBalanceForAccount(account.id)
                // Log.d("BalanceCalc Future [${account.name}]", "Starting current balance: $currentBalance")

                // Find transactions (actual & projected) occurring AFTER 'now' up to the 'endOfDayFuture'.
                val futureTransactions = _allProjectedTransactions.value
                    .filter {
                        it.accountId == account.id && it.date != null &&
                                it.date.after(now) && !it.date.after(endOfDayFuture) // Between now (exclusive) and end of future date (inclusive)
                    }

                val futureChanges = futureTransactions.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                // Log.d("BalanceCalc Future [${account.name}]", "Sum of future changes: $futureChanges")

                // The projected balance is the current balance plus future changes.
                val projectedBalance = currentBalance + futureChanges
                Log.d("BalanceCalc Future [${account.name}]", "Final projected balance for $date: $projectedBalance")

                account.id to projectedBalance
            }
        }
    }
    // --- End Calendar Functions ---

    // --- START: Budget Category Drilldown ---
    fun getMonthlyBudgetTransactionsForCategory(category: Category): List<Transaction> {
        val startOfMonth = getStartOfCurrentMonth()
        return budgetTransactions
            .filter { it.date != null && !it.date.before(startOfMonth) && it.category == category }
            .sortedByDescending { it.date }
    }

    fun getWeeklyBudgetTransactionsForCategory(category: Category): List<Transaction> {
        val startOfWeek = getStartOfCurrentWeek()
        return budgetTransactions
            .filter { it.date != null && !it.date.before(startOfWeek) && it.category == category }
            .sortedByDescending { it.date }
    }
    // --- END: Budget Category Drilldown ---

    // --- START: Budget Goal Setting ---
    fun setBudget(monthlyGoal: Double, weeklyGoal: Double) {
        val trackerId = activeTracker?.id ?: return
        db.collection("trackers").document(trackerId)
            .update(
                mapOf(
                    "monthlyBudget" to monthlyGoal,
                    "weeklyBudget" to weeklyGoal
                )
            )
            .addOnFailureListener {
                Log.e("FinancialViewModel", "Failed to update budget", it)
            }
    }
    // --- END: Budget Goal Setting ---

    // --- Navigation and UI event handlers ---
    fun navigateToDashboard() { currentPage = Page.DASHBOARD }
    fun navigateToAccountHistory(accountId: String) {
        selectedAccountIdForHistory = accountId
        currentPage = Page.ACCOUNT_HISTORY
        clearDateFilter()
        onCategoryFilterChanged(null)
        onSearchQueryChanged("") // Clear search
    }

    fun navigateToUpcomingHistory() { currentPage = Page.UPCOMING_HISTORY }
    fun navigateToManageRequests() { currentPage = Page.MANAGE_REQUESTS }
    fun navigateToManageAccounts() { currentPage = Page.MANAGE_ACCOUNTS }
    fun navigateToFutureScope() {
        currentPage = Page.FUTURE_SCOPE
        clearFutureScopeDateFilter() // Reset filter when navigating to this screen
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun navigateToCalendarScope() {
        currentPage = Page.CALENDAR_SCOPE
        // processFutureTransactions() is called within recalculateDerivedLists, which is triggered by listeners
        onCalendarDateSelected(LocalDate.now()) // Select today initially
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun navigateToCalendarWithDate(date: LocalDate) {
        currentPage = Page.CALENDAR_SCOPE
        // This will set the selected date AND trigger a recalculation
        onCalendarDateSelected(date)
    }

    fun navigateToBudget() { currentPage = Page.BUDGET }

    fun navigateToScanner() { currentPage = Page.SCANNER }

    fun navigateToGraphs() { currentPage = Page.GRAPHS }

    fun onAddTracker() { navigatingToSelection = true }
    fun onBackFromTrackerSelection() { navigatingToSelection = false }
    fun onCategoryFilterChanged(category: Category?) {
        selectedCategoryFilter = category
        recalculateDerivedLists()
    }

    // --- START: Search Handlers ---
    fun onSearchQueryChanged(newQuery: String) {
        searchQuery = newQuery
        recalculateDerivedLists()
    }
    fun clearSearchQuery() {
        onSearchQueryChanged("")
    }
    // --- END: Search Handlers ---

    fun onUpcomingItemSelected(item: UpcomingDisplayItem) { upcomingItemForAction = item }
    fun clearUpcomingItemForAction() { upcomingItemForAction = null }

    fun onTransactionSelected(transaction: Transaction) {
        // --- START: Updated logic ---
        // Always load the specific transaction instance.
        // If it's a projected one, find it in the _allProjectedTransactions list.
        // If it's a real one, find it in the _transactions list.
        itemToEdit = _allProjectedTransactions.value.find { it.id == transaction.id }
            ?: _transactions.value.find { it.id == transaction.id }
                    ?: transaction // Fallback
        // --- END: Updated logic ---
    }

    fun clearItemToEdit() { itemToEdit = null }
    fun onRecurringItemSelected(item: RecurringItem) { selectedRecurringItem = item }
    fun clearSelectedRecurringItem() { selectedRecurringItem = null }

    fun clearItemToDelete() { itemToDelete = null } // Function to close the new dialog

    fun editUpcomingItem(item: UpcomingDisplayItem) {
        // --- START: Updated logic ---
        // Find the *projected transaction* associated with this display item
        itemToEdit = _allProjectedTransactions.value.find {
            it.recurringItemId == item.originalId &&
                    it.date == item.date
        } ?: _transactions.value.find { it.id == item.originalId } // Fallback for one-off
        // --- END: Updated logic ---
        clearUpcomingItemForAction()
    }

    fun switchToTrackerSelection() {
        activeTracker = null
        // Call the new clear function and the simplified detach function
        clearDataState()
        detachListeners() // This now only clears activeListeners
    }
    // --- End Navigation and UI event handlers ---
}