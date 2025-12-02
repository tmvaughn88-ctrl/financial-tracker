// ui/BudgetScreen.kt

package com.example.financialtracker.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financialtracker.Account
import com.example.financialtracker.Category
import com.example.financialtracker.FinancialViewModel
import com.example.financialtracker.Transaction
import java.text.NumberFormat
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(viewModel: FinancialViewModel) {
    BackHandler { viewModel.navigateToDashboard() }

    val activeTracker = viewModel.activeTracker
    var monthlyGoal by rememberSaveable {
        mutableStateOf(activeTracker?.monthlyBudget?.takeIf { it > 0 }?.toString() ?: "")
    }
    var weeklyGoal by rememberSaveable {
        mutableStateOf(activeTracker?.weeklyBudget?.takeIf { it > 0 }?.toString() ?: "")
    }

    // --- START: New state for the details dialog ---
    var categoryForDialog by remember { mutableStateOf<Category?>(null) }
    var transactionListForDialog by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var dialogTitle by remember { mutableStateOf("") }
    // --- END: New state for the details dialog ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Budget") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- START: New Available Budget Card ---
            item {
                AvailableBudgetInfoCard(
                    monthlyAvailable = viewModel.maxAvailableMonthlyBudget,
                    weeklyAvailable = viewModel.maxAvailableWeeklyBudget
                )
                Spacer(Modifier.height(24.dp))
            }
            // --- END: New Available Budget Card ---


            // --- Goal Setting Card ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Set Budget Goals",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            "Budgets track variable, non-recurring spending (e.g., food, entertainment, one-off purchases). Your fixed bills (rent, utilities, etc.) are managed separately as Recurring Items.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = monthlyGoal,
                            onValueChange = { monthlyGoal = it },
                            label = { Text("Monthly Spending Goal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color(0xFF4ADE80), unfocusedIndicatorColor = Color.Gray,
                                focusedLabelColor = Color.White, unfocusedLabelColor = Color.Gray
                            )
                        )
                        OutlinedTextField(
                            value = weeklyGoal,
                            onValueChange = { weeklyGoal = it },
                            label = { Text("Weekly Spending Goal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color(0xFF4ADE80), unfocusedIndicatorColor = Color.Gray,
                                focusedLabelColor = Color.White, unfocusedLabelColor = Color.Gray
                            )
                        )
                        Button(
                            onClick = {
                                val monthly = monthlyGoal.toDoubleOrNull() ?: 0.0
                                val weekly = weeklyGoal.toDoubleOrNull() ?: 0.0
                                viewModel.setBudget(monthly, weekly)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Budget Goals")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // --- Progress Display ---
            item {
                BudgetProgressCard(
                    title = "This Month's Spending",
                    spent = viewModel.totalMonthlySpending,
                    goal = activeTracker?.monthlyBudget ?: 0.0,
                    spendingBreakdown = viewModel.monthlySpendingByCategory,
                    // --- START: Add click handler ---
                    onCategoryClick = { category ->
                        dialogTitle = "This Month's Spending: ${category.displayName}"
                        transactionListForDialog = viewModel.getMonthlyBudgetTransactionsForCategory(category)
                        categoryForDialog = category // This shows the dialog
                    }
                    // --- END: Add click handler ---
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                BudgetProgressCard(
                    title = "This Week's Spending",
                    spent = viewModel.totalWeeklySpending,
                    goal = activeTracker?.weeklyBudget ?: 0.0,
                    spendingBreakdown = viewModel.weeklySpendingByCategory,
                    // --- START: Add click handler ---
                    onCategoryClick = { category ->
                        dialogTitle = "This Week's Spending: ${category.displayName}"
                        transactionListForDialog = viewModel.getWeeklyBudgetTransactionsForCategory(category)
                        categoryForDialog = category // This shows the dialog
                    }
                    // --- END: Add click handler ---
                )
            }
        }

        // --- START: Add new details dialog ---
        if (categoryForDialog != null) {
            BudgetCategoryDetailDialog(
                title = dialogTitle,
                transactions = transactionListForDialog,
                accounts = viewModel.accounts, // Need this for accountName
                onDismiss = {
                    categoryForDialog = null
                    transactionListForDialog = emptyList()
                    dialogTitle = ""
                },
                onTransactionClick = { transaction ->
                    viewModel.onTransactionSelected(transaction)
                }
            )
        }
        // --- END: Add new details dialog ---
    }
}

// --- START: New Composable for Available Budget ---
@Composable
fun AvailableBudgetInfoCard(
    monthlyAvailable: Double,
    weeklyAvailable: Double
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Available to Budget",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                "(Recurring Income minus Recurring Bills)",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            HorizontalDivider(color = Color(0xFF374151))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Monthly",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        currencyFormat.format(monthlyAvailable),
                        color = if (monthlyAvailable >= 0) Color(0xFF4ADE80) else Color(0xFFF87171),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Weekly",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        currencyFormat.format(weeklyAvailable),
                        color = if (weeklyAvailable >= 0) Color(0xFF4ADE80) else Color(0xFFF87171),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Use these numbers as a guide to set your goals below.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
// --- END: New Composable for Available Budget ---


@Composable
fun BudgetProgressCard(
    title: String,
    spent: Double,
    goal: Double,
    spendingBreakdown: Map<Category, Double>,
    onCategoryClick: (Category) -> Unit // --- ADD THIS PARAMETER ---
) {
    // Format currency
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    val spentFormatted = currencyFormat.format(spent)
    val goalFormatted = currencyFormat.format(goal)

    // Calculate progress
    val progress = if (goal > 0) (spent / goal).toFloat() else 0f
    val remaining = goal - spent
    val remainingFormatted = currencyFormat.format(remaining)

    // Determine colors
    val progressColor = if (progress > 1f) Color(0xFF4ADE80) else Color(0xFFF87171) // Red if over, Green if under
    val remainingColor = if (remaining < 0) Color(0xFFF87171) else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                "Tracks non-recurring expenses.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(16.dp))

            if (goal > 0) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)), // Use RoundedCornerShape
                    color = progressColor,
                    trackColor = Color(0xFF374151)
                )
                Spacer(Modifier.height(8.dp))

                // Text: "$Spent / $Goal"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(spentFormatted, color = progressColor, fontWeight = FontWeight.SemiBold)
                    Text(goalFormatted, color = Color.Gray)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF374151))
                Spacer(Modifier.height(16.dp))

                // Text: "Remaining"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (remaining >= 0) "Remaining:" else "Over Budget:",
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                    Text(
                        text = remainingFormatted,
                        color = remainingColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // --- START OF UPDATED SECTION ---
                if (spendingBreakdown.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Spending Breakdown:",
                        color = Color.LightGray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        spendingBreakdown.forEach { (category, total) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)) // Add clip for ripple
                                    .clickable { onCategoryClick(category) } // MAKE CLICKABLE
                                    .padding(horizontal = 8.dp, vertical = 4.dp), // Add padding
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.displayName,
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = currencyFormat.format(total),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                // --- END OF UPDATED SECTION ---

            } else {
                // Show if no goal is set
                Text(
                    "No budget goal set. Add a goal above to track your spending.",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// --- START: New Composable for the Details Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetCategoryDetailDialog(
    title: String,
    transactions: List<Transaction>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onTransactionClick: (Transaction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (transactions.isEmpty()) {
                Text("No transactions found for this category in this period.", color = Color.Gray)
            } else {
                // Use Box with fixed height to make it scrollable without taking full screen
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn {
                        items(transactions, key = { it.id }) { transaction ->
                            val accountName = accounts.find { it.id == transaction.accountId }?.name ?: "Unknown"
                            TransactionRow(
                                transaction = transaction,
                                accountName = accountName,
                                onClick = {
                                    onTransactionClick(transaction)
                                    onDismiss() // Close this dialog to open the edit dialog
                                }
                            )
                            HorizontalDivider(color = Color(0xFF374151))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
// --- END: New Composable for the Details Dialog ---