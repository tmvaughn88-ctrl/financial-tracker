// CalendarScopeScreen.kt

package com.example.financialtracker.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// --- START: Added Missing Imports ---
import com.example.financialtracker.FinancialViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
// --- END: Added Missing Imports ---
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.LocalDate // Ensure LocalDate is imported
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScopeScreen(viewModel: FinancialViewModel) {
    val transactionsForDate = viewModel.transactionsForSelectedCalendarDate
    val upcomingEvents = viewModel.upcomingTransactionsByDate
    val pastEvents = viewModel.pastTransactionsByDate
    val today = LocalDate.now() // Get today's date

    // --- START: UPDATE CALENDAR STATE ---
    // Use the ViewModel's selected date to determine the starting month
    val selectedMonth = remember(viewModel.selectedCalendarDate) {
        YearMonth.from(viewModel.selectedCalendarDate)
    }
    val startMonth = remember { selectedMonth.minusMonths(100) }
    val endMonth = remember { selectedMonth.plusMonths(100) }
    // --- END: UPDATE CALENDAR STATE ---
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = selectedMonth, // <-- Use the selected month
        firstDayOfWeek = firstDayOfWeek
    )

    // --- START: ADD LAUNCHED EFFECT ---
    // This effect will scroll the calendar if the selected date changes
    // (e.g., from a notification)
    LaunchedEffect(selectedMonth) {
        state.animateScrollToMonth(selectedMonth)
    }
    // --- END: ADD LAUNCHED EFFECT ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar Scope") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToDashboard() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111827),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF111827)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalCalendar(
                state = state,
                dayContent = { day ->
                    val isPast = day.date.isBefore(today)
                    val hasUpcoming = upcomingEvents[day.date].orEmpty().isNotEmpty()
                    val hasPast = pastEvents[day.date].orEmpty().isNotEmpty()

                    Day(
                        day = day,
                        isSelected = viewModel.selectedCalendarDate == day.date,
                        // Show green for today/future with events, red for past with events
                        dotColor = when {
                            !isPast && hasUpcoming -> Color(0xFF4ADE80) // Green for upcoming/today
                            isPast && hasPast -> Color(0xFFF87171)     // Red for past
                            else -> null                          // No dot
                        }
                    ) { clickedDay ->
                        viewModel.onCalendarDateSelected(clickedDay.date)
                    }
                },
                monthHeader = { month ->
                    val daysOfWeek = month.weekDays.first().map { it.date.dayOfWeek }
                    MonthHeader(daysOfWeek = daysOfWeek, month = month.yearMonth)
                }
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ProjectedBalancesCard(
                        viewModel = viewModel,
                        totalBalance = viewModel.projectedTotalBalanceForSelectedDate,
                        accountBalances = viewModel.projectedAccountBalancesForSelectedDate
                    )
                }

                if (transactionsForDate.isNotEmpty()) {
                    item {
                        Text(
                            text = if (viewModel.selectedCalendarDate.isBefore(today)) "Recorded Transactions" else "Scheduled Transactions",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    }
                }

                if (transactionsForDate.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions found for this day.", // Generic message
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(transactionsForDate, key = { "calendar-${it.id}" }) { transaction ->
                        val accountName = viewModel.accounts.find { acc -> acc.id == transaction.accountId }?.name
                            ?: "Unknown"
                        TransactionRow(
                            transaction = transaction,
                            accountName = accountName,
                            onClick = { viewModel.onTransactionSelected(transaction) }
                        )
                        HorizontalDivider(color = Color(0xFF374151))
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ProjectedBalancesCard(
    viewModel: FinancialViewModel,
    totalBalance: Double?,
    accountBalances: Map<String, Double>
) {
    val today = LocalDate.now()
    val titleText = if (viewModel.selectedCalendarDate == today) {
        "Current Balances"
    } else if (viewModel.selectedCalendarDate.isBefore(today)) {
        "Balances at End of Day"
    } else {
        "Projected Balances for Day"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                titleText, // Use dynamic title
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

            totalBalance?.let {
                val balanceColor = if (it >= 0) Color(0xFF4ADE80) else Color(0xFFF87171)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Balance:", color = Color.LightGray, fontWeight = FontWeight.SemiBold) // Simplified label
                    Text(String.format("$%,.2f", it), color = balanceColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF374151))
            }

            if (accountBalances.isNotEmpty()) {
                viewModel.accounts.sortedBy { it.name }.forEach { account ->
                    val balance = accountBalances[account.id]
                    if (balance != null) {
                        val balanceColor = if (balance >= 0) Color.White else Color(0xFFF87171)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(account.name, color = Color.Gray)
                            Text(String.format("$%,.2f", balance), color = balanceColor)
                        }
                    }
                }
            } else {
                Text("No account balances available for this day.", color = Color.Gray, fontSize = 12.sp) // Adjusted message
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun Day(
    day: CalendarDay,
    isSelected: Boolean,
    dotColor: Color?,
    onClick: (CalendarDay) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color = if (isSelected) Color(0xFF4ADE80) else Color.Transparent)
            .clickable { onClick(day) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 14.sp
            )
            dotColor?.let { color ->
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color) // Use the provided color
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MonthHeader(daysOfWeek: List<java.time.DayOfWeek>, month: YearMonth) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            for (dayOfWeek in daysOfWeek) {
                Text(
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
            }
        }
        HorizontalDivider(color = Color.Gray)
    }
}