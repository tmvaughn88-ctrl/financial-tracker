// FutureScopeScreen.kt

package com.example.financialtracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financialtracker.FinancialViewModel
import com.example.financialtracker.TransactionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureScopeScreen(viewModel: FinancialViewModel) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    var isPickingStartDate by remember { mutableStateOf(true) }
    // We might not need the trigger state anymore if we reset the datePickerState correctly
    val datePickerState = rememberDatePickerState()

    // Date Picker Dialog Logic
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
                // Reset to picking start date if dismissed
                isPickingStartDate = true
                viewModel.clearFutureScopeDateFilter() // Clear selection if dismissed
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val tz = TimeZone.getDefault()
                            val offset = tz.getOffset(utcMillis)
                            val correctedDate = Date(utcMillis - offset)

                            if (isPickingStartDate) {
                                viewModel.setFutureScopeDateFilter(correctedDate, null) // Set start date
                                isPickingStartDate = false // Now we need the end date
                                Toast.makeText(context, "Start date set. Select end date.", Toast.LENGTH_SHORT).show()

                                // --- START OF CHANGE 1: Reset state and reopen ---
                                // Close the current dialog instance
                                showDatePicker = false
                                // Use coroutine to reopen after a short delay, allowing recomposition
                                coroutineScope.launch {
                                    delay(150) // Slightly longer delay might help
                                    // Reset datePickerState *before* showing again
                                    // Note: Direct reset isn't straightforward. Recreating might be needed if this fails.
                                    // Let's try relying on recomposition first.
                                    showDatePicker = true // Reopen the picker for end date
                                }
                                // --- END OF CHANGE 1 ---

                            } else { // Picking End Date
                                viewModel.futureScopeStartDate?.let { startDate ->
                                    if (correctedDate.before(startDate)) {
                                        Toast.makeText(context, "End date cannot be before start date.", Toast.LENGTH_SHORT).show()
                                        // Keep picker open for correction? Or close and force restart? Let's close.
                                        showDatePicker = false
                                        isPickingStartDate = true // Reset to start picking again
                                        viewModel.clearFutureScopeDateFilter() // Clear invalid selection
                                    } else {
                                        val calEnd = Calendar.getInstance().apply { time = correctedDate }
                                        viewModel.setFutureScopeEndOfDay(calEnd)
                                        viewModel.setFutureScopeDateFilter(startDate, calEnd.time)
                                        showDatePicker = false // Close dialog after successful end date selection
                                        isPickingStartDate = true // Reset for the next time
                                    }
                                } ?: run {
                                    Toast.makeText(context, "Error: Start date not set. Please select start date again.", Toast.LENGTH_LONG).show()
                                    isPickingStartDate = true // Reset state
                                    showDatePicker = false
                                }
                            }
                        } ?: run { // No date selected when OK was pressed
                            showDatePicker = false // Close dialog
                            isPickingStartDate = true // Reset picking state
                            viewModel.clearFutureScopeDateFilter() // Clear any partial selection
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    isPickingStartDate = true // Reset picking state on cancel
                    viewModel.clearFutureScopeDateFilter() // Clear any partial selection
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(
                state = datePickerState,
                // Optionally add a title to guide the user
                title = {
                    Text(
                        text = if (isPickingStartDate) "Select Start Date" else "Select End Date",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Future Scope") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToDashboard() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Filter buttons Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { viewModel.setFutureScopeNext7Days() }, modifier = Modifier.weight(1f)) {
                    Text("Next 7 Days")
                }
                Button(onClick = { viewModel.setFutureScopeNext30Days() }, modifier = Modifier.weight(1f)) {
                    Text("Next 30 Days")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Filter buttons Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isPickingStartDate = true
                        // Reset date picker state before showing
                        // datePickerState.selectedDateMillis = null // Deprecated way, rely on recomposition
                        showDatePicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Custom Range")
                }
                Button(
                    onClick = { viewModel.clearFutureScopeDateFilter() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Clear/All")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Selected Date Range
            if (viewModel.futureScopeStartDate != null && viewModel.futureScopeEndDate != null) {
                Text(
                    text = "Range: ${dateFormat.format(viewModel.futureScopeStartDate!!)} - ${dateFormat.format(viewModel.futureScopeEndDate!!)}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                )
            }


            // Display Estimated Total Balance
            viewModel.estimatedTotalFutureBalance?.let { balance ->
                val balanceColor = if (balance >= 0) Color(0xFF4ADE80) else Color(0xFFF87171)
                Text(
                    text = "Est. Total Balance: ${String.format("$%,.2f", balance)}",
                    color = balanceColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp).align(Alignment.CenterHorizontally)
                )
            } ?: run {
                Text(
                    text = "Showing all future transactions.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp).align(Alignment.CenterHorizontally)
                )
            }

            // Display Per-Account End Balances
            if (viewModel.estimatedAccountEndBalances.isNotEmpty()) {
                Text(
                    "Est. Account Balances:",
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Column(
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    viewModel.accounts.sortedBy { it.name }.forEach { account ->
                        val endBalance = viewModel.estimatedAccountEndBalances[account.id]
                        val displayBalance = endBalance ?: viewModel.getBalanceForAccount(account.id)
                        val balanceColor = if (displayBalance >= 0) Color(0xFF4ADE80) else Color(0xFFF87171)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${account.name}:", color = Color.White, fontSize = 14.sp)
                            Text(
                                text = String.format("%,.2f", displayBalance),
                                color = balanceColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))

            // Transaction List
            LazyColumn(
                modifier = Modifier.fillMaxWidth(), // Use available width
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.futureScopeTransactions.isEmpty()) {
                    item {
                        Text(
                            if (viewModel.futureScopeStartDate != null) "No upcoming transactions in the selected range." else "No future transactions found.",
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    items(viewModel.futureScopeTransactions, key = { "future-${it.id}" }) { transaction ->
                        val accountName = viewModel.accounts.find { acc -> acc.id == transaction.accountId }?.name ?: "Unknown"
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
