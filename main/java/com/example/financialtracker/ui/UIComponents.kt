// ui/UIComponents.kt

package com.example.financialtracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financialtracker.*
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------
// --- START: UTILITY COMPONENTS (DEFINED ONCE AT TOP) ---
// ---------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = Color.White,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun HelpText(text: String) {
    Text(text = text, color = Color.LightGray, fontSize = 14.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectorDropdown(
    selectedCategory: Category,
    onCategorySelected: (Category) -> Unit,
    isSelectable: Boolean = true,
    label: String = "Select Category"
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = remember { Category.values().toList() }
    val currentSelection = selectedCategory.displayName

    val darkTextFieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color.White,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color(0xFF4ADE80),
        unfocusedIndicatorColor = Color.Gray,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.Gray,
        disabledTextColor = Color.Gray
    )


    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (isSelectable) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentSelection,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = darkTextFieldColors,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            enabled = isSelectable
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1F2937))
        ) {
            DropdownMenuItem(
                text = { Text("All Categories", color = Color.White) },
                onClick = {
                    onCategorySelected(Category.OTHER)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName, color = Color.White) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun DatePartSelector(
    format: String,
    calendar: Calendar,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { onValueChange(1) }) {
            Icon(Icons.Filled.ArrowDropUp, contentDescription = "Increase", tint = Color.White)
        }
        Text(
            text = SimpleDateFormat(format, Locale.getDefault()).format(calendar.time),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = { onValueChange(-1) }) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Decrease", tint = Color.White)
        }
    }
}

// ---------------------------------------------------
// --- END: UTILITY COMPONENTS ---
// ---------------------------------------------------


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostponeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Date) -> Unit
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val tz = TimeZone.getDefault()
                        val offset = tz.getOffset(utcMillis)
                        val correctedDate = Date(utcMillis - offset)
                        onConfirm(correctedDate)
                    }
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}


@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onAdjustBalance: (String, Double) -> Unit,
    onSignOut: () -> Unit,
    onSwitchTracker: () -> Unit,
    onDeleteTracker: () -> Unit,
    onManageAccounts: () -> Unit,
    onManageBudget: () -> Unit, // Added this
    isCreator: Boolean,
    accounts: List<Account>
) {
    var showAdjustBalanceDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onManageAccounts, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage Accounts")
                }
                Button(onClick = onManageBudget, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage Budget")
                }
                Button(onClick = { showAdjustBalanceDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Adjust Balance")
                }
                Button(onClick = onSwitchTracker, modifier = Modifier.fillMaxWidth()) {
                    Text("Switch / Create Tracker")
                }
                if (isCreator) {
                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Tracker")
                    }
                }
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log Out")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showAdjustBalanceDialog) {
        AdjustBalanceDialog(
            accounts = accounts,
            onDismiss = { showAdjustBalanceDialog = false },
            onConfirm = { accountId, newBalance ->
                onAdjustBalance(accountId, newBalance)
                showAdjustBalanceDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Are you sure?") },
            text = { Text("This will permanently delete the tracker and all its data for everyone. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTracker()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Delete It")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringItemEntryForm(
    accounts: List<Account>,
    onAdd: (RecurringItem) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedCategory by remember { mutableStateOf(Category.OTHER) }
    var isFluctuating by remember { mutableStateOf(false) }

    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    // FIX: Define the standard Material 3 colors object once
    val darkTextFieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color.White,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color(0xFF4ADE80),
        unfocusedIndicatorColor = Color.Gray,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.Gray
    )

    Column {
        OutlinedTextField(
            value = description, onValueChange = { description = it }, label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = amount, onValueChange = { amount = it }, label = { Text("Amount (Estimate for fluctuating)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors
        )
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = accountMenuExpanded,
            onExpandedChange = { accountMenuExpanded = !accountMenuExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedAccount?.let { "${it.name} (${it.type})" } ?: "Select Account",
                onValueChange = {},
                readOnly = true,
                label = { Text("Account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                colors = darkTextFieldColors,
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = accountMenuExpanded,
                onDismissRequest = { accountMenuExpanded = false }
            ) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text("${account.name} (${account.type})") },
                        onClick = {
                            selectedAccount = account
                            accountMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CategorySelectorDropdown(
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            label = "Select Category"
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("First Payment Date", color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DatePartSelector("MMM", selectedDate) { change ->
                selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.MONTH, change) }
            }
            DatePartSelector("dd", selectedDate) { change ->
                selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, change) }
            }
            DatePartSelector("yyyy", selectedDate) { change ->
                selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.YEAR, change) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { frequency = Frequency.WEEKLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.WEEKLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Weekly") }
                Button(onClick = { frequency = Frequency.BIWEEKLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.BIWEEKLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Bi-Weekly") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { frequency = Frequency.MONTHLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.MONTHLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Monthly") }
                Button(onClick = { frequency = Frequency.YEARLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.YEARLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Yearly") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { transactionType = TransactionType.INCOME },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transactionType == TransactionType.INCOME) Color(0xFF4ADE80) else Color.DarkGray,
                    contentColor = if (transactionType == TransactionType.INCOME) Color.Black else Color.White
                )
            ) { Text("Income") }
            Button(
                onClick = { transactionType = TransactionType.EXPENSE },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transactionType == TransactionType.EXPENSE) Color(0xFFF87171) else Color.DarkGray,
                    contentColor = if (transactionType == TransactionType.EXPENSE) Color.Black else Color.White
                )
            ) { Text("Expense") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isFluctuating, onCheckedChange = { isFluctuating = it })
            Text("This is a fluctuating bill (e.g., Utilities)", color = Color.White)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val amountDouble = amount.toDoubleOrNull()
                if (description.isNotBlank() && amountDouble != null && selectedAccount != null) {
                    onAdd(
                        RecurringItem(
                            description = description,
                            amount = amountDouble,
                            type = transactionType,
                            frequency = frequency,
                            nextDate = selectedDate.time,
                            category = selectedCategory,
                            isFluctuating = isFluctuating,
                            accountId = selectedAccount!!.id
                        )
                    )
                    description = ""
                    amount = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Item to List")
        }
    }
}

@Composable
fun UpcomingItemActionDialog(
    item: UpcomingDisplayItem,
    onDismiss: () -> Unit,
    onPayEarly: () -> Unit,
    onEdit: () -> Unit,
    onDismissItem: () -> Unit,
    onPostpone: () -> Unit,
    onMarkAsPaid: () -> Unit,
    onSplitPayment: () -> Unit,
    // FIX 6: Added new parameter for scanning
    onScanBill: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.description) },
        text = { Text("What would you like to do with this upcoming item?") },
        confirmButton = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.isPostponed) {
                    Button(onClick = onMarkAsPaid, modifier = Modifier.fillMaxWidth()) {
                        Text("Paid")
                    }
                } else {
                    // --- NEW BUTTON FOR SCANNING ---
                    // Only show scan button if the item is fluctuating (NEEDS_CONFIRMATION)
                    if (item.isRecurring && item.confirmationState == ConfirmationState.NEEDS_CONFIRMATION) {
                        Button(
                            onClick = onScanBill,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCE56)), // Yellow/Orange
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan Fluctuating Bill")
                        }
                    }
                    // --- END NEW BUTTON ---

                    Button(onClick = onPayEarly, modifier = Modifier.fillMaxWidth()) {
                        Text("Pay Now")
                    }
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit")
                    }
                    Button(onClick = onSplitPayment, modifier = Modifier.fillMaxWidth()) {
                        Text("Split Payment")
                    }
                    Button(onClick = onDismissItem, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.fillMaxWidth()) {
                        Text("Dismiss")
                    }
                    if (item.isRecurring) {
                        Button(onClick = onPostpone, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.fillMaxWidth()) {
                            Text("Postpone")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Use the App") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionTitle("Dashboard & Adding Transactions")
                    HelpText("• The Dashboard shows your total balances and a list of individual accounts. Tap on any account to see its specific history.")
                    HelpText("• Use the 'Add New Transaction' form to record income, expenses, or transfers between accounts.")
                    HelpText("• Use the 'Scan Receipt / Total' button to quickly add expenses using your device's camera.")
                    HelpText("• Use the 'Is this a one-off bill?' checkbox for expenses (like a car repair) that you don't want to count against your variable spending budget.")
                }

                item {
                    SectionTitle("Managing Bills & Income")
                    HelpText("• Go to Settings > Manage Accounts, and use the 'Add Recurring Item' form to add fixed bills (rent, insurance) and income (paychecks).")
                    HelpText("• These recurring items are NOT counted in your budget. Instead, they are used to calculate your 'Available to Budget' amount.")
                }

                item {
                    SectionTitle("Budgeting (New!)")
                    HelpText("• Go to Settings > Manage Budget.")
                    HelpText("• 'Available to Budget': This card shows your total recurring income minus your total recurring bills. This is the maximum 'surplus' you have to spend or save each period.")
                    HelpText("• 'Set Budget Goals': Set a monthly or weekly goal for your *variable spending* (food, entertainment, etc.). This goal is separate from your fixed bills.")
                    HelpText("• 'Spending Progress': These cards track your variable spending against your goal. Any transaction *not* marked as a bill and *not* a recurring item will appear here.")
                    HelpText("• Tap any category in the 'Spending Breakdown' to see a list of transactions for that category.")
                }

                item {
                    SectionTitle("Managing Upcoming Items")
                    HelpText("• The 'Upcoming Items' list on the dashboard shows your next few transactions. Tap an item to see options like 'Pay Now', 'Split Payment', or 'Postpone'.")
                    HelpText("• If an item is fluctuating, tap it and select **'Scan Fluctuating Bill'** to use OCR on your online bill screen to confirm the amount and date.")
                    HelpText("• Swipe an upcoming item to the right to temporarily hide it. To see all hidden items again, tap the Refresh icon (🔄).")
                    HelpText("• Postponing a payment moves that single payment to a new date you choose, without affecting the original recurring schedule.")
                }

                item {
                    SectionTitle("Future Scope (Forecasting)")
                    HelpText("• Tap the 'View Future List' button on the dashboard to see a projection of all your future transactions, including generated instances of your recurring items.")
                    HelpText("• Use the filter buttons ('Next 7 Days', 'Next 30 Days', etc.) to see your expected cash flow for specific periods.")
                    HelpText("• Tap 'View Calendar' for a monthly view. Dots show days with transactions (Green=Today/Future, Red=Past). Tap any day to see details and projected balances.")
                }

                item {
                    SectionTitle("Charts & Trends (New!)")
                    HelpText("• Tap 'View Charts' to see a pie chart breakdown of your current monthly variable spending and a comparison of your spending over the last three months.")
                }


                item {
                    SectionTitle("Account History")
                    HelpText("• From the dashboard, tap any account balance to view its full transaction history.")
                    HelpText("• You can filter this history by date range and category.")
                    HelpText("• Tap any transaction in the history to edit its details (amount, date, account, etc.).")
                    HelpText("• Editing an upcoming recurring item will change that instance only and will not affect the recurring template.")
                }

                item {
                    SectionTitle("Settings & Sharing")
                    HelpText("• Tap the Settings icon (⚙️) on the dashboard to access more features.")
                    HelpText("• Manage Accounts: Add new checking/savings accounts or add/delete your recurring bills and income.")
                    HelpText("• Manage Budget: Set your variable spending goals and track progress.")
                    HelpText("• Adjust Balance: Correct an account's balance if it doesn't match your bank statement.")
                    HelpText("• Switch/Create Tracker: Create a new budget tracker or switch between existing ones.")
                    HelpText("• Sharing: On the dashboard, copy the 'Shareable Tracker ID' and give it to a partner. As the creator, you can approve their join requests from the 'Manage Join Requests' icon (👥).")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ---------------------------------------------------
// --- START: CORE COMPONENTS (MUST BE AT ROOT LEVEL) ---
// ---------------------------------------------------


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustBalanceDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var newBalance by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    val context = LocalContext.current


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccount?.let { "${it.name} (${it.type})" } ?: "Select Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.type})") },
                                onClick = {
                                    selectedAccount = account
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = newBalance,
                    onValueChange = { newBalance = it },
                    label = { Text("Correct Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balance = newBalance.toDoubleOrNull()
                    if (selectedAccount != null && balance != null) {
                        onConfirm(selectedAccount!!.id, balance)
                    } else {
                        Toast.makeText(context, "Please select an account and enter a balance.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = selectedAccount != null && newBalance.isNotBlank()
            ) {
                Text("Adjust")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilter(viewModel: FinancialViewModel) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var isPickingStartDate by remember { mutableStateOf(true) }

    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val tz = TimeZone.getDefault()
                            val offset = tz.getOffset(utcMillis)
                            val correctedDate = Date(utcMillis - offset)

                            val start = if (isPickingStartDate) correctedDate else viewModel.startDateFilter
                            val end = if (!isPickingStartDate) correctedDate else viewModel.endDateFilter
                            viewModel.setDateFilter(start, end)
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    isPickingStartDate = true
                    showDatePicker = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(viewModel.startDateFilter?.let { dateFormat.format(it) } ?: "Start Date")
            }
            Button(
                onClick = {
                    if (viewModel.startDateFilter == null) {
                        Toast.makeText(context, "Please select a start date first", Toast.LENGTH_SHORT).show()
                    } else {
                        isPickingStartDate = false
                        showDatePicker = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(viewModel.endDateFilter?.let { dateFormat.format(it) } ?: "End Date")
            }
        }
        if (viewModel.startDateFilter != null || viewModel.endDateFilter != null) {
            Button(
                onClick = { viewModel.clearDateFilter() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Clear Filter")
            }
        }
    }
}


@Composable
fun CategoryTotalDisplay(category: Category, total: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF374151))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total for ${category.displayName}:", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(String.format("$%,.2f", total), color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilter(
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = remember { Category.values().toList() }
    val currentSelection = selectedCategory?.displayName ?: "All Categories"

    val darkTextFieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color.White,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color(0xFF4ADE80),
        unfocusedIndicatorColor = Color.Gray,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.Gray,
        disabledTextColor = Color.Gray
    )


    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentSelection,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter by Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = darkTextFieldColors,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            enabled = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1F2937))
        ) {
            DropdownMenuItem(
                text = { Text("All Categories", color = Color.White) },
                onClick = {
                    onCategorySelected(null)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName, color = Color.White) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}


@Composable
fun RecurringItemDetailDialog(
    item: RecurringItem,
    onDismiss: () -> Unit,
    onSkipNext: () -> Unit,
    onDeleteSingle: () -> Unit,
    onDeleteSeries: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.description) },
        text = { Text("Manage this recurring item.") },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSkipNext) {
                    Text("Skip Next Occurrence")
                }
                Button(onClick = onDeleteSingle, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))) {
                    Text("Delete Just This One")
                }
                Button(onClick = onDeleteSeries, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171))) {
                    Text("Delete This & Future")
                }
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Close")
                }
            }
        }
    )
}

// --- START: FIX DELETE DIALOG LOGIC ---
@Composable
fun DeleteConfirmationDialog(
    item: Any,
    onDismiss: () -> Unit,
    onDeleteSingle: () -> Unit,
    onDeleteSeries: () -> Unit
) {
    val isRecurringItemTemplate = item is RecurringItem
    val isRecurringInstance = item is Transaction && (item as Transaction).recurringItemId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Item?") },
        text = {
            Column {
                if (isRecurringItemTemplate) {
                    Text("This is a recurring item template. How would you like to delete it?")
                } else if (isRecurringInstance) {
                    Text("This is an instance of a recurring item. How would you like to delete it?")
                } else if (item is Transaction) {
                    Text("Are you sure you want to permanently delete this transaction? This action cannot be undone.")
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRecurringItemTemplate || isRecurringInstance) {
                    // Show "Instance" / "Series" for both templates and instances
                    Button(
                        onClick = onDeleteSingle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete This Instance Only")
                    }
                    Button(
                        onClick = onDeleteSeries,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete This & All Future")
                    }
                } else if (item is Transaction) {
                    // Show a simple "Delete" for any one-off history item
                    Button(
                        onClick = onDeleteSingle,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Transaction")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", textAlign = TextAlign.Center)
                }
            }
        },
        dismissButton = {}
    )
}
// --- END: FIX DELETE DIALOG LOGIC ---


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemDialog(
    item: Any,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onUpdateRecurringItem: (RecurringItem) -> Unit, // Re-added parameter name to match MainActivity
    onUpdateTransaction: (Transaction) -> Unit,
    onConvertToRecurring: (Transaction, Frequency, Boolean) -> Unit,
    onDelete: (Any) -> Unit
) {
    // --- START: MODIFIED LOGIC ---
    val transaction = item as? Transaction
    // If item is not a Transaction, dismiss. This dialog now ONLY edits Transactions.
    if (transaction == null) {
        onDismiss()
        return
    }

    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var description by remember { mutableStateOf(transaction.description) }
    var selectedCategory by remember { mutableStateOf<Category?>(transaction.category) }

    // Check if this was originally a recurring item (template) OR a projected instance
    val isProjectedInstance = transaction.recurringItemId != null && transaction.id.contains("-")
    val isRecurringInstance = transaction.recurringItemId != null // True if it has a link to a template

    // This state is only for converting a one-off TO recurring
    var isFluctuating by remember { mutableStateOf(false) }

    var selectedAccountId by remember { mutableStateOf(transaction.accountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    // NOTE: If it's a projected instance, we don't allow changing isBill because it MUST be a bill.
    var isBill by remember { mutableStateOf(transaction.isBill) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = transaction.date?.time
    )
    var selectedDate by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                time = transaction.date ?: Date()
            }
        )
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val tz = TimeZone.getDefault()
                            val offset = tz.getOffset(millis)
                            selectedDate.time = Date(millis - offset)
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    // --- END: MODIFIED LOGIC ---


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") }, // Always editing a transaction instance
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") }
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(dateFormat.format(selectedDate.time))
                }

                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = !accountMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == selectedAccountId }?.let { "${it.name} (${it.type})" } ?: "Select Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.type})") },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                CategorySelectorDropdown(
                    selectedCategory = selectedCategory ?: Category.OTHER,
                    onCategorySelected = { newCategory -> selectedCategory = newCategory },
                    isSelectable = transaction.transferId == null,
                    label = "Select Category"
                )

                // Only show "convert to recurring" options if it's NOT already a recurring instance
                if (!isRecurringInstance) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isFluctuating, onCheckedChange = { isFluctuating = it })
                        Text("This is a fluctuating bill")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isBill,
                        onCheckedChange = { isBill = it },
                        enabled = transaction.type == TransactionType.EXPENSE && !isProjectedInstance
                    )
                    Text("This is a one-off bill (exclude from budget)")
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val newAmount = amount.toDoubleOrNull()
                        if (newAmount != null && selectedCategory != null) {
                            onUpdateTransaction(transaction.copy(
                                description = description,
                                amount = newAmount,
                                date = selectedDate.time,
                                category = selectedCategory!!,
                                accountId = selectedAccountId,
                                isBill = if (isProjectedInstance) true else isBill
                            ))
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Changes")
                }

                // Only show "convert to recurring" options if it's NOT already a recurring instance
                if (!isRecurringInstance) {
                    Spacer(Modifier.height(16.dp))
                    Text("Convert to Recurring & Save:", style = MaterialTheme.typography.labelMedium)
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                onConvertToRecurring(
                                    transaction.copy(description = description, amount = amount.toDoubleOrNull() ?: 0.0, date = selectedDate.time, category = selectedCategory ?: Category.OTHER, accountId = selectedAccountId, isBill = isBill),
                                    Frequency.WEEKLY,
                                    isFluctuating
                                )
                                onDismiss()
                            }, modifier = Modifier.weight(1f)) { Text("Weekly") }
                            Button(onClick = {
                                onConvertToRecurring(
                                    transaction.copy(description = description, amount = amount.toDoubleOrNull() ?: 0.0, date = selectedDate.time, category = selectedCategory ?: Category.OTHER, accountId = selectedAccountId, isBill = isBill),
                                    Frequency.BIWEEKLY,
                                    isFluctuating
                                )
                                onDismiss()
                            }, modifier = Modifier.weight(1f)) { Text("Bi-Weekly") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                onConvertToRecurring(
                                    transaction.copy(description = description, amount = amount.toDoubleOrNull() ?: 0.0, date = selectedDate.time, category = selectedCategory ?: Category.OTHER, accountId = selectedAccountId, isBill = isBill),
                                    Frequency.MONTHLY,
                                    isFluctuating
                                )
                                onDismiss()
                            }, modifier = Modifier.weight(1f)) { Text("Monthly") }
                            Button(onClick = {
                                onConvertToRecurring(
                                    transaction.copy(description = description, amount = amount.toDoubleOrNull() ?: 0.0, date = selectedDate.time, category = selectedCategory ?: Category.OTHER, accountId = selectedAccountId, isBill = isBill),
                                    Frequency.YEARLY,
                                    isFluctuating
                                )
                                onDismiss()
                            }, modifier = Modifier.weight(1f)) { Text("Yearly") }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onDelete(item)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        },
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecurringItemDialog(
    item: RecurringItem,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onUpdate: (RecurringItem) -> Unit,
) {
    var description by remember { mutableStateOf(item.description) }
    var amount by remember { mutableStateOf(item.amount.toString()) }
    var transactionType by remember { mutableStateOf(item.type) }
    var frequency by remember { mutableStateOf(item.frequency) }
    var selectedCategory by remember { mutableStateOf(item.category) }
    var isFluctuating by remember { mutableStateOf(item.isFluctuating) }

    var selectedAccountId by remember { mutableStateOf(item.accountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    // Use Calendar for nextDate editing, if present
    var nextDate by remember {
        mutableStateOf(Calendar.getInstance().apply { time = item.nextDate ?: Date() })
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = item.nextDate?.time
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val tz = TimeZone.getDefault()
                            val offset = tz.getOffset(millis)
                            nextDate.time = Date(millis - offset)
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    val darkTextFieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color(0xFF4ADE80), unfocusedIndicatorColor = Color.Gray,
        focusedLabelColor = Color.White, unfocusedLabelColor = Color.Gray
    )


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Recurring Item") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), colors = darkTextFieldColors
                )

                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = !accountMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == selectedAccountId }?.let { "${it.name} (${it.type})" } ?: "Select Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                        colors = darkTextFieldColors,
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.type})") },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Next Date: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(nextDate.time)}")
                }

                Spacer(modifier = Modifier.height(8.dp))
                CategorySelectorDropdown(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    label = "Select Category"
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { frequency = Frequency.WEEKLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.WEEKLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Weekly") }
                        Button(onClick = { frequency = Frequency.BIWEEKLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.BIWEEKLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Bi-Weekly") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { frequency = Frequency.MONTHLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.MONTHLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Monthly") }
                        Button(onClick = { frequency = Frequency.YEARLY }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (frequency == Frequency.YEARLY) Color(0xFF4ADE80) else Color.DarkGray)) { Text("Yearly") }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { transactionType = TransactionType.INCOME },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (transactionType == TransactionType.INCOME) Color(0xFF4ADE80) else Color.DarkGray,
                            contentColor = if (transactionType == TransactionType.INCOME) Color.Black else Color.White
                        )
                    ) { Text("Income") }
                    Button(
                        onClick = { transactionType = TransactionType.EXPENSE },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (transactionType == TransactionType.EXPENSE) Color(0xFFF87171) else Color.DarkGray,
                            contentColor = if (transactionType == TransactionType.EXPENSE) Color.Black else Color.White
                        )
                    ) { Text("Expense") }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFluctuating, onCheckedChange = { isFluctuating = it })
                    Text(
                        "This is a fluctuating bill (requires manual confirmation)",
                        color = Color.White
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amountDouble = amount.toDoubleOrNull()
                if (description.isNotBlank() && amountDouble != null && selectedAccountId.isNotBlank()) {
                    onUpdate(item.copy(
                        description = description,
                        amount = amountDouble,
                        type = transactionType,
                        frequency = frequency,
                        nextDate = nextDate.time,
                        category = selectedCategory,
                        isFluctuating = isFluctuating,
                        accountId = selectedAccountId
                    ))
                }
                onDismiss()
            }) { Text("Save Changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun BalanceDisplay(title: String, balance: Double, type: String, modifier: Modifier = Modifier) {
    val balanceColor = if (balance >= 0) Color(0xFF4ADE80) else Color(0xFFF87171)
    val icon = when (type.lowercase(Locale.getDefault())) {
        "checking" -> Icons.Default.AccountBalanceWallet
        "savings" -> Icons.Default.Savings
        "credit card" -> Icons.Default.CreditCard
        "investment", "ira" -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Default.AttachMoney
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = type,
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(title.uppercase(Locale.getDefault()), fontSize = 16.sp, color = Color.LightGray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("$%,.2f", balance),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = balanceColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryForm(
    accounts: List<Account>,
    onAdd: (description: String, amount: Double, type: TransactionType, accountId: String, date: Date, category: Category, isBill: Boolean) -> Unit,
    onAddTransfer: (description: String, amount: Double, fromAccountId: String, toAccountId: String, date: Date) -> Unit,
    // FIX 7: Added new parameter for navigation
    onNavigateToScanner: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var isTransfer by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(Category.OTHER) }

    var isBill by remember { mutableStateOf(false) }

    var fromAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var toAccount by remember { mutableStateOf(accounts.getOrNull(1) ?: accounts.firstOrNull()) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var transferToMenuExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val tz = TimeZone.getDefault()
                            val offset = tz.getOffset(millis)
                            selectedDate.time = Date(millis - offset)
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    LaunchedEffect(isTransfer) {
        if (isTransfer) {
            selectedCategory = Category.SAVINGS_TRANSFER
            isBill = false
        }
    }

    LaunchedEffect(transactionType) {
        if (transactionType == TransactionType.INCOME) {
            isBill = false
        }
    }

    // FIX: Define the standard Material 3 colors object once
    val darkTextFieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color.White,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color(0xFF4ADE80),
        unfocusedIndicatorColor = Color.Gray,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.Gray
    )


    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- UPDATED TITLE ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add New Transaction", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                // --- NEW: Scan Button ---
                Button(
                    onClick = onNavigateToScanner, // FIX 7: Use the new parameter
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Scan Receipt", fontSize = 12.sp)
                }
                // --- END NEW: Scan Button ---
            }

            Spacer(modifier = Modifier.height(16.dp))
            // --- END UPDATED TITLE ROW ---

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(if (isTransfer) "Transfer Description" else "Description") },
                modifier = Modifier.fillMaxWidth(),
                colors = darkTextFieldColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = darkTextFieldColors
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!isTransfer) {
                CategorySelectorDropdown(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    isSelectable = true,
                    label = "Select Category"
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text(dateFormat.format(selectedDate.time))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if(isTransfer) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = accountMenuExpanded, onExpandedChange = { accountMenuExpanded = !accountMenuExpanded }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = fromAccount?.let { "${it.name} (${it.type})" } ?: "From", onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) }, modifier = Modifier.menuAnchor(), colors = darkTextFieldColors)
                        ExposedDropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                            accounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} (${account.type})") }, onClick = { fromAccount = account; accountMenuExpanded = false }) }
                        }
                    }
                    Text("to", color = Color.White)
                    ExposedDropdownMenuBox(expanded = transferToMenuExpanded, onExpandedChange = { transferToMenuExpanded = !transferToMenuExpanded }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = toAccount?.let { "${it.name} (${it.type})" } ?: "To", onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transferToMenuExpanded) }, modifier = Modifier.menuAnchor(), colors = darkTextFieldColors)
                        ExposedDropdownMenu(expanded = transferToMenuExpanded, onDismissRequest = { transferToMenuExpanded = false }) {
                            accounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} (${account.type})") }, onClick = { toAccount = account; transferToMenuExpanded = false }) }
                        }
                    }
                }
            } else {
                ExposedDropdownMenuBox(expanded = accountMenuExpanded, onExpandedChange = { accountMenuExpanded = !accountMenuExpanded }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fromAccount?.let { "${it.name} (${it.type})" } ?: "Select Account", onValueChange = {}, readOnly = true, label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) }, modifier = Modifier.menuAnchor(),
                        colors = darkTextFieldColors
                    )
                    ExposedDropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                        accounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} (${account.type})") }, onClick = { fromAccount = account; accountMenuExpanded = false }) }
                    }
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Expense", color = if (transactionType == TransactionType.EXPENSE && !isTransfer) Color.White else Color.Gray)
                Switch(
                    checked = transactionType == TransactionType.INCOME,
                    onCheckedChange = {
                        transactionType = if (it) TransactionType.INCOME else TransactionType.EXPENSE
                    },
                    enabled = !isTransfer,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4ADE80),
                        uncheckedThumbColor = Color.Gray,
                        checkedTrackColor = Color(0xFF2F6C44),
                    )
                )
                Text("Income", color = if (transactionType == TransactionType.INCOME && !isTransfer) Color.White else Color.Gray)
                Spacer(Modifier.width(16.dp))
                Text("Transfer", color = if (isTransfer) Color.White else Color.Gray)
                Switch(checked = isTransfer, onCheckedChange = { isTransfer = it })
            }

            if (!isTransfer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Checkbox(
                        checked = isBill,
                        onCheckedChange = { isBill = it },
                        enabled = transactionType == TransactionType.EXPENSE
                    )
                    Text(
                        "Is this a one-off bill? (Exclude from budget)",
                        color = if (transactionType == TransactionType.EXPENSE) Color.White else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    if (description.isNotBlank() && amountDouble != null) {
                        if (isTransfer) {
                            if (fromAccount != null && toAccount != null) {
                                onAddTransfer(description, amountDouble, fromAccount!!.id, toAccount!!.id, selectedDate.time)
                            }
                        } else {
                            if (fromAccount != null) {
                                onAdd(description, amountDouble, transactionType, fromAccount!!.id, selectedDate.time, selectedCategory, isBill)
                            }
                        }
                        description = ""
                        amount = ""
                        selectedDate = Calendar.getInstance()
                        isTransfer = false
                        isBill = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
                enabled = (isTransfer && fromAccount != null && toAccount != null) || (!isTransfer && fromAccount != null)
            ) {
                Text(if (isTransfer) "Add Transfer" else "Add Transaction", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun TransactionRow(
    transaction: Transaction,
    accountName: String,
    onClick: () -> Unit
) {
    val amountColor = if (transaction.type == TransactionType.INCOME) Color(0xFF4ADE80) else Color(0xFFF87171)
    val sign = if (transaction.type == TransactionType.INCOME) "+" else "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.description, fontSize = 16.sp, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$accountName · ${transaction.category.displayName}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
                if (transaction.transferId != null) {
                    Text(" (Transfer)", color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                } else if (transaction.recurringItemId != null) {
                    Text(" (Recurring)", color = Color(0xFF60A5FA), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                } else if (transaction.isBill) {
                    Text(" (Bill)", color = Color.Cyan, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
                if(transaction.wasPaidEarly) {
                    Text(" (Paid Early)", color = Color.Cyan, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            // Safely display the date, providing a default if it's null
            Text(
                transaction.date?.let { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(it) } ?: "No Date",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Text(
            text = "$sign$${String.format("%,.2f", transaction.amount)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}


@Composable
fun UpcomingItemRow(
    item: UpcomingDisplayItem,
    onClick: () -> Unit
) {
    val amountColor = if (item.type == TransactionType.INCOME) Color(0xFF4ADE80) else Color(0xFFF87171)
    val indicatorColor = when {
        item.isPostponed -> Color.Blue
        item.confirmationState == ConfirmationState.ACTION_REQUIRED -> Color.Red
        item.confirmationState == ConfirmationState.NEEDS_CONFIRMATION -> Color.Yellow
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF374151))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if ((item.isRecurring && item.confirmationState != ConfirmationState.NONE) || item.isPostponed) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(indicatorColor, shape = CircleShape)
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.description, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Date: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(item.date)}", // Use 'yyyy'
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Row {
                    if (item.accountName != null) {
                        Text(
                            text = "${item.accountName} ",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                    if (item.isRecurring) {
                        Text(" (Recurring)", color = Color(0xFF60A5FA), fontSize = 12.sp)
                    }
                }
            }
            Text(
                text = String.format("$%,.2f", item.amount),
                color = amountColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPaymentDialog(
    item: UpcomingDisplayItem,
    onDismiss: () -> Unit,
    onConfirm: (numberOfPayments: Int, dates: List<Date>) -> Unit
) {
    var numberOfPayments by remember { mutableStateOf("2") }
    val numPaymentsInt = numberOfPayments.toIntOrNull() ?: 2
    var selectedDates by remember { mutableStateOf<List<Date>>(emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val tz = TimeZone.getDefault()
                            val offset = tz.getOffset(utcMillis)
                            val correctedDate = Date(utcMillis - offset)
                            selectedDates = selectedDates + correctedDate
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Payment for ${item.description}") },
        text = {
            Column {
                OutlinedTextField(
                    value = numberOfPayments,
                    onValueChange = { if (it.toIntOrNull() != null || it.isEmpty()) numberOfPayments = it },
                    label = { Text("Number of Payments") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showDatePicker = true },
                    enabled = selectedDates.size < numPaymentsInt
                ) {
                    Text("Select Date for Payment ${selectedDates.size + 1}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(selectedDates.withIndex().toList()) { (index, date) ->
                        Text("Payment ${index + 1}: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)}")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(numPaymentsInt, selectedDates) },
                enabled = selectedDates.size == numPaymentsInt && numPaymentsInt > 1
            ) {
                Text("Confirm Split")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

