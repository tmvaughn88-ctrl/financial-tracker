// MainActivity.kt

package com.example.financialtracker.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.financialtracker.* import com.example.financialtracker.ui.theme.FinancialTrackerTheme
import kotlinx.coroutines.delay
import java.util.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.financialtracker.NotificationHelper
import com.example.financialtracker.NotificationWorker
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private val viewModel: FinancialViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            NotificationWorker.schedule(this)
        } else {
            Toast.makeText(
                this,
                "Notifications permission denied. You will not receive reminders.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    NotificationWorker.schedule(this)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            NotificationWorker.schedule(this)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        NotificationHelper.createNotificationChannel(this)

        setContent {
            FinancialTrackerTheme {
                LaunchedEffect(key1 = true) {
                    askNotificationPermission()
                }
                FinancialTrackerApp(viewModel)
            }
        }

        intent?.let { handleNotificationIntent(it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleNotificationIntent(intent: Intent) {
        val targetDateMillis = intent.getLongExtra("TARGET_DATE_MILLIS", -1L)
        if (targetDateMillis != -1L) {
            val targetDate = Instant.ofEpochMilli(targetDateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            viewModel.navigateToCalendarWithDate(targetDate)

            intent.removeExtra("TARGET_DATE_MILLIS")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        viewModel.attachDataListeners()
    }

    override fun onPause() {
        super.onPause()
        viewModel.detachListeners()
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "spinning_dollar_transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dollar_rotation_y_animation"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dollar_fade_animation"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AttachMoney,
            contentDescription = "Loading",
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .alpha(alpha),
            tint = Color(0xFF4ADE80)
        )
    }
}

@Composable
fun LoginScreen(viewModel: FinancialViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Financial Tracker", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = TextFieldDefaults.colors(
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
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = TextFieldDefaults.colors(
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
        )
        Spacer(modifier = Modifier.height(24.dp))

        viewModel.authError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        viewModel.passwordResetStatus?.let {
            val statusColor = if (it.startsWith("Failed")) MaterialTheme.colorScheme.error else Color(0xFF4ADE80)
            Text(it, color = statusColor, modifier = Modifier.padding(bottom = 8.dp), textAlign = TextAlign.Center)
        }

        Button(
            onClick = { viewModel.signIn(email, password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.signUp(email, password) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Sign Up")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { viewModel.sendPasswordResetEmail(email) }) {
            Text("Forgot Password?")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSelectionScreen(viewModel: FinancialViewModel) {
    var trackerName by remember { mutableStateOf("") }
    var trackerIdToJoin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a Tracker") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Create a New Shared Tracker", fontSize = 20.sp, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = trackerName,
                onValueChange = { trackerName = it },
                label = { Text("Tracker Name (e.g., 'Family Budget')") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
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
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.createNewTracker(trackerName) },
                enabled = trackerName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create") }

            Spacer(modifier = Modifier.height(48.dp))
            HorizontalDivider(color = Color.Gray)
            Spacer(modifier = Modifier.height(48.dp))

            Text("... or Join an Existing Tracker", fontSize = 20.sp, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = trackerIdToJoin,
                onValueChange = { trackerIdToJoin = it },
                label = { Text("Enter Tracker ID from your partner") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
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
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.requestToJoinTracker(trackerIdToJoin) },
                enabled = trackerIdToJoin.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send Join Request") }
            viewModel.joinTrackerError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            viewModel.joinRequestStatus?.let {
                Text(it, color = Color(0xFF4ADE80), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FinancialTrackerApp(viewModel: FinancialViewModel) {
    var timeElapsed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initializeListeners()
        delay(3000L)
        timeElapsed = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111827)) {
        if (!viewModel.isUserLoggedIn) {
            LoginScreen(viewModel = viewModel)
        } else {
            val showSplashScreen = viewModel.isLoadingTrackers || !timeElapsed

            if (showSplashScreen) {
                SplashScreen()
            } else {
                if (viewModel.activeTracker == null) {
                    if (viewModel.navigatingToSelection) {
                        TrackerSelectionScreen(viewModel = viewModel)
                    } else {
                        TrackerListScreen(viewModel = viewModel)
                    }
                } else {
                    // Main App Content when logged in and tracker selected
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (viewModel.currentPage) {
                            Page.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                            Page.ACCOUNT_HISTORY -> AccountHistoryScreen(viewModel = viewModel)
                            Page.UPCOMING_HISTORY -> UpcomingHistoryScreen(viewModel = viewModel)
                            Page.MANAGE_REQUESTS -> ManageRequestsScreen(viewModel = viewModel)
                            Page.MANAGE_ACCOUNTS -> ManageAccountsScreen(viewModel = viewModel)
                            Page.FUTURE_SCOPE -> FutureScopeScreen(viewModel = viewModel)
                            Page.CALENDAR_SCOPE -> CalendarScopeScreen(viewModel = viewModel)
                            Page.BUDGET -> BudgetScreen(viewModel = viewModel)
                            // FIX 3: Routing logic for Scanner must check for active item
                            Page.SCANNER -> {
                                if (viewModel.upcomingItemForAction != null && viewModel.upcomingItemForAction!!.isRecurring) {
                                    ScanBillScreen(
                                        viewModel = viewModel,
                                        upcomingItem = viewModel.upcomingItemForAction!!
                                    )
                                } else {
                                    ScanTransactionScreen(viewModel = viewModel)
                                }
                            }
                            Page.GRAPHS -> GraphScreen(viewModel = viewModel)
                            else -> DashboardScreen(viewModel = viewModel)
                        }

                        // --- Dialogs (FIXED) ---
                        viewModel.itemToEdit?.let { item ->
                            // FIX 1: Use EditRecurringItemDialog if the item is a RecurringItem template
                            if (item is RecurringItem) {
                                EditRecurringItemDialog(
                                    item = item,
                                    accounts = viewModel.accounts,
                                    onDismiss = { viewModel.clearItemToEdit() },
                                    onUpdate = { viewModel.updateRecurringItem(it) }
                                )
                            } else {
                                // If it's a Transaction (or projected item), use EditItemDialog
                                EditItemDialog(
                                    item = item,
                                    accounts = viewModel.accounts,
                                    onDismiss = { viewModel.clearItemToEdit() },
                                    onUpdateRecurringItem = { /* parameter re-added in UIComponents.kt */ },
                                    onUpdateTransaction = { transaction ->
                                        viewModel.updateTransaction(transaction)
                                        viewModel.clearItemToEdit()
                                    },
                                    onConvertToRecurring = { transaction, frequency, isFluctuating ->
                                        viewModel.convertToRecurring(transaction, frequency, isFluctuating)
                                        viewModel.clearItemToEdit()
                                    },
                                    onDelete = {
                                        viewModel.itemToDelete = it
                                        viewModel.clearItemToEdit()
                                    }
                                )
                            }
                        }

                        viewModel.upcomingItemForAction?.let { item ->
                            UpcomingItemActionDialog(
                                item = item,
                                onDismiss = { viewModel.clearUpcomingItemForAction() },
                                onPayEarly = { viewModel.payItemEarly(item) },
                                onEdit = { viewModel.editUpcomingItem(item) },
                                onDismissItem = {
                                    viewModel.dismissUpcomingItem(item)
                                    viewModel.clearUpcomingItemForAction()
                                },
                                onPostpone = {
                                    val recurringItem = viewModel.recurringItems.find { it.id == item.originalId }
                                    if (recurringItem != null) viewModel.postponeRecurringItem(recurringItem)
                                    viewModel.clearUpcomingItemForAction()
                                },
                                onMarkAsPaid = {
                                    val recurringItem = viewModel.recurringItems.find { it.id == item.originalId }
                                    if (recurringItem != null) viewModel.markPostponedAsPaid(recurringItem)
                                    viewModel.clearUpcomingItemForAction()
                                },
                                onSplitPayment = {
                                    viewModel.itemToSplit = item
                                    viewModel.clearUpcomingItemForAction()
                                },
                                // FIX 2: Added onScanBill parameter
                                onScanBill = {
                                    // Navigate to the specific bill scanning screen, item stays active
                                    viewModel.currentPage = Page.SCANNER
                                }
                            )
                        }

                        viewModel.itemToSplit?.let { item ->
                            SplitPaymentDialog(
                                item = item,
                                onDismiss = { viewModel.itemToSplit = null },
                                // FIX 3: Explicitly typed parameters for SplitPaymentDialog lambda
                                onConfirm = { numberOfPayments: Int, dates: List<Date> ->
                                    viewModel.confirmSplitPayment(item, numberOfPayments, dates)
                                }
                            )
                        }

                        viewModel.selectedRecurringItem?.let { item ->
                            RecurringItemDetailDialog(
                                item = item,
                                onDismiss = { viewModel.clearSelectedRecurringItem() },
                                onSkipNext = { viewModel.skipNextRecurringOccurrence(item) },
                                onDeleteSingle = { viewModel.deleteSingleRecurringOccurrence(item) },
                                onDeleteSeries = { viewModel.deleteRecurringThisAndFuture(item) }
                            )
                        }

                        viewModel.itemToPostpone?.let {
                            PostponeDialog(
                                onDismiss = { viewModel.itemToPostpone = null },
                                onConfirm = { newDate ->
                                    viewModel.confirmPostpone(newDate)
                                }
                            )
                        }

                        viewModel.itemToDelete?.let { item ->
                            DeleteConfirmationDialog(
                                item = item,
                                onDismiss = { viewModel.clearItemToDelete() },
                                onDeleteSingle = {
                                    viewModel.deleteSingleRecurringOccurrence(item)
                                    viewModel.clearItemToDelete()
                                },
                                onDeleteSeries = {
                                    viewModel.deleteRecurringThisAndFuture(item)
                                    viewModel.clearItemToDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerListScreen(viewModel: FinancialViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111827),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF111827),
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddTracker() }) {
                Icon(Icons.Default.Add, contentDescription = "Add or Join Tracker")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.userTrackersList) { tracker ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectActiveTracker(tracker) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tracker.name, fontSize = 18.sp, color = Color.White)
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open Tracker", tint = Color.White)
                    }
                }
            }
        }
    }
}


@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val backCallback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }
    SideEffect {
        backCallback.isEnabled = enabled
    }
    val backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current) {
        "No OnBackPressedDispatcherOwner was provided via LocalOnBackPressedDispatcherOwner"
    }.onBackPressedDispatcher
    DisposableEffect(backDispatcher) {
        backDispatcher.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: FinancialViewModel) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    BackHandler {
        viewModel.switchToTrackerSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.activeTracker?.name ?: "Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111827),
                    titleContentColor = Color.White
                ),
                actions = {
                    if (viewModel.isCreator) {
                        BadgedBox(
                            badge = {
                                if (viewModel.joinRequests.isNotEmpty()) {
                                    Badge { Text("${viewModel.joinRequests.size}") }
                                }
                            }
                        ) {
                            IconButton(onClick = { viewModel.navigateToManageRequests() }) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "Manage Join Requests", tint = Color.White)
                            }
                        }
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Help", tint = Color.White)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF111827)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = WindowInsets.navigationBars
                .add(WindowInsets(left = 16.dp, right = 16.dp, bottom = 16.dp))
                .asPaddingValues(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shareable Tracker ID: ${viewModel.activeTracker?.id}",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Tracker ID", viewModel.activeTracker?.id)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Tracker ID Copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Tracker ID", tint = Color.Gray)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BalanceDisplay(
                        title = "Checking Accounts",
                        balance = viewModel.totalCheckingBalance,
                        type = "Checking",
                        modifier = Modifier.weight(1f)
                    )
                    BalanceDisplay(
                        title = "Savings & Investments",
                        balance = viewModel.totalSavingsBalance,
                        type = "Savings",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(viewModel.accounts, key = { it.id }) { account ->
                        BalanceDisplay(
                            title = account.name,
                            balance = viewModel.getBalanceForAccount(account.id),
                            type = account.type,
                            modifier = Modifier
                                .width(200.dp)
                                .clickable { viewModel.navigateToAccountHistory(account.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                EntryForm(
                    accounts = viewModel.accounts,
                    onAdd = { description, amount, type, accountId, date, category, isBill ->
                        viewModel.addTransaction(description, amount, type, accountId, date, category, isBill)
                    },
                    onAddTransfer = { description, amount, fromAccountId, toAccountId, date ->
                        viewModel.addTransfer(description, amount, fromAccountId, toAccountId, date)
                    },
                    onNavigateToScanner = { viewModel.navigateToScanner() }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }


            // --- Upcoming Items Section ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Upcoming Items",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.clickable { viewModel.navigateToUpcomingHistory() }
                    )
                    IconButton(onClick = { viewModel.refreshUpcomingItems() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Upcoming Items",
                            tint = Color.White
                        )
                    }
                }
            }

            if (viewModel.upcomingItems.isEmpty()) {
                item {
                    Text("No upcoming items found.", color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                items(viewModel.upcomingItems, key = { "upcoming-${it.originalId}-${it.date.time}" }) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.dismissUpcomingItem(item)
                                return@rememberSwipeToDismissBoxState true
                            }
                            false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            val color by animateColorAsState(
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFFF87171)
                                    else -> Color.Transparent
                                }, label = "background_color_anim"
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Dismiss Item",
                                    tint = Color.White,
                                )
                            }
                        }
                    ) {
                        UpcomingItemRow(
                            item = item,
                            onClick = { viewModel.onUpcomingItemSelected(item) }
                        )
                    }
                }
            }
            // --- End Upcoming Items ---

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Future/Calendar Scope Buttons (UPDATED) ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.navigateToFutureScope() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View Future List")
                    }
                    Button(
                        onClick = { viewModel.navigateToCalendarScope() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View Calendar")
                    }
                    // *** NEW: Button for the Graphs Page ***
                    Button(
                        onClick = { viewModel.navigateToGraphs() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
                    ) {
                        Text("View Charts", fontSize = 12.sp)
                    }
                    // ***************************************
                }
            }
            // --- End Buttons ---


            // --- Transaction History Section ---
            item {
                Text(
                    "Transaction History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            items(viewModel.transactions.take(25), key = { "history-${it.id}-${it.date?.time}" }) { transaction ->
                Column {
                    val accountName = viewModel.accounts.find { it.id == transaction.accountId }?.name ?: "Unknown"
                    TransactionRow(
                        transaction = transaction,
                        accountName = accountName,
                        onClick = { viewModel.onTransactionSelected(transaction) }
                    )
                    HorizontalDivider(color = Color(0xFF374151))
                }
            }
            // --- End Transaction History ---
        }

        // --- Dialogs ---
        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                onAdjustBalance = { accountId, newBalance ->
                    viewModel.adjustBalance(accountId, newBalance)
                },
                onSignOut = { viewModel.signOut() },
                onSwitchTracker = { viewModel.switchToTrackerSelection() },
                onDeleteTracker = { viewModel.deleteTracker() },
                onManageAccounts = { viewModel.navigateToManageAccounts() },
                onManageBudget = {
                    viewModel.navigateToBudget()
                    showSettingsDialog = false
                },
                isCreator = viewModel.isCreator,
                accounts = viewModel.accounts
            )
        }
        if (showHelpDialog) {
            HelpDialog(onDismiss = { showHelpDialog = false })
        }
        // --- End Dialogs ---
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHistoryScreen(viewModel: FinancialViewModel) {
    BackHandler { viewModel.navigateToDashboard() }
    val accountName = viewModel.accounts.find { it.id == viewModel.selectedAccountIdForHistory }?.name ?: "Account"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$accountName History") },
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
            contentPadding = WindowInsets.systemBars
                .add(WindowInsets(left = 16.dp, right = 16.dp, bottom = 16.dp))
                .asPaddingValues()
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    label = { Text("Search Descriptions") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFF4ADE80),
                        unfocusedIndicatorColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    ),
                    trailingIcon = {
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearchQuery() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = Color.Gray)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                DateRangeFilter(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                CategoryFilter(
                    selectedCategory = viewModel.selectedCategoryFilter,
                    onCategorySelected = { viewModel.onCategoryFilterChanged(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(viewModel.accountHistoryTransactions, key = { "full-history-${it.id}" }) { transaction ->
                Column {
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

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingHistoryScreen(viewModel: FinancialViewModel) {
    BackHandler { viewModel.navigateToDashboard() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Upcoming Items") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToDashboard() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshUpcomingItems() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
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
            contentPadding = WindowInsets.systemBars
                .add(WindowInsets(left = 16.dp, right = 16.dp, bottom = 16.dp))
                .asPaddingValues()
        ) {
            item{
                Spacer(modifier = Modifier.height(16.dp))
                DateRangeFilter(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (viewModel.allUpcomingItems.isEmpty()) {
                item {
                    Text("No upcoming items found.", color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                items(viewModel.allUpcomingItems, key = { "all-upcoming-${it.originalId}-${it.date.time}" }) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.dismissUpcomingItem(item)
                                return@rememberSwipeToDismissBoxState true
                            }
                            false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            val color by animateColorAsState(
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFFF87171)
                                    else -> Color.Transparent
                                }, label = "background_color_anim"
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Dismiss Item",
                                    tint = Color.White,
                                )
                            }
                        }
                    ) {
                        UpcomingItemRow(
                            item = item,
                            onClick = { viewModel.onUpcomingItemSelected(item) }
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageRequestsScreen(viewModel: FinancialViewModel) {
    BackHandler { viewModel.navigateToDashboard() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Join Requests") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (viewModel.joinRequests.isEmpty()) {
                item {
                    Text("No pending join requests.", color = Color.Gray)
                }
            } else {
                items(viewModel.joinRequests) { request ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Request from:", fontWeight = FontWeight.Bold, color=Color.White)
                            Text(request.requestingUserEmail, color=Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.acceptJoinRequest(request) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Accept")
                                }
                                Button(
                                    onClick = { viewModel.denyJoinRequest(request) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Deny")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountsScreen(viewModel: FinancialViewModel) {
    var newAccountName by remember { mutableStateOf("") }
    val accountTypes = listOf("Checking", "Savings", "Credit Card", "Investment", "IRA")
    var newAccountType by remember { mutableStateOf(accountTypes[0]) }
    var isTypeMenuExpanded by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    BackHandler { viewModel.navigateToDashboard() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Accounts & Bills") },
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
            // --- Section for Adding Accounts ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Add New Account", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newAccountName,
                            onValueChange = { newAccountName = it },
                            label = { Text("Account Name (e.g., 'Vacation Fund')") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
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
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = isTypeMenuExpanded,
                            onExpandedChange = { isTypeMenuExpanded = !isTypeMenuExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newAccountType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Account Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeMenuExpanded) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color(0xFF4ADE80),
                                    unfocusedIndicatorColor = Color.Gray,
                                    focusedLabelColor = Color.White,
                                    unfocusedLabelColor = Color.Gray,
                                    disabledTextColor = Color.Gray,
                                    disabledTrailingIconColor = Color.Gray
                                ),
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = isTypeMenuExpanded,
                                onDismissRequest = { isTypeMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF1F2937))
                            ) {
                                accountTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type, color = Color.White) },
                                        onClick = {
                                            newAccountType = type
                                            isTypeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.addAccount(newAccountName, newAccountType)
                                newAccountName = ""
                                newAccountType = accountTypes[0]
                            },
                            enabled = newAccountName.isNotBlank() && newAccountType.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Account")
                        }
                    }
                }
            }

            // --- Section for Existing Accounts ---
            item {
                Text("Existing Accounts", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
            }
            items(viewModel.accounts, key = { "account-${it.id}" }) { account ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF374151))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(account.name, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(account.type, fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = {
                            accountToDelete = account
                            showDeleteConfirmDialog = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Account", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // --- Section Divider ---
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray)
                Spacer(Modifier.height(16.dp))
            }

            // --- Section for Existing Recurring Items ---
            item {
                Text("Recurring Bills & Income", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
            }
            items(viewModel.recurringItems, key = { "recurring-${it.id}" }) { item ->
                val currencyFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
                val amountColor = if (item.type == TransactionType.INCOME) Color(0xFF4ADE80) else Color(0xFFF87171)
                val sign = if (item.type == TransactionType.INCOME) "+" else "-"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            viewModel.itemToEdit = item
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF374151))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.description, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Next: ${item.nextDate?.let { currencyFormat.format(it) } ?: "N/A"}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                "Frequency: ${item.frequency.name.lowercase().replaceFirstChar { it.titlecase() }}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "$sign$${String.format("%,.2f", item.amount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // --- Section Divider ---
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray)
                Spacer(Modifier.height(16.dp))
            }

            // --- Section for Adding Recurring Items ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Add Recurring Item", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        RecurringItemEntryForm(
                            accounts = viewModel.accounts,
                            onAdd = { newItem ->
                                val userId = viewModel.auth.currentUser?.uid ?: ""
                                viewModel.updateRecurringItem(newItem.copy(userId = userId))
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Delete Confirmation Dialog for ACCOUNTS ---
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                accountToDelete = null
                showDeleteConfirmDialog = false
            },
            title = { Text("Delete ${accountToDelete?.name}?") },
            text = { Text("Are you sure? This will delete the account and all of its associated transactions permanently. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        accountToDelete?.let { viewModel.deleteAccount(it.id) }
                        accountToDelete = null
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    accountToDelete = null
                    showDeleteConfirmDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}