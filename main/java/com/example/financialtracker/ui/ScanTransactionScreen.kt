// ui/ScanTransactionScreen.kt

package com.example.financialtracker.ui

import android.Manifest
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.financialtracker.Account
import com.example.financialtracker.Category
import com.example.financialtracker.FinancialViewModel
import com.example.financialtracker.TransactionType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Date
import java.util.concurrent.Executors
import java.util.regex.Pattern

// --- New Data Class to hold result before confirmation ---
data class ScanResult(val amount: Double, val description: String)
// --------------------------------------------------------

// --- Helper Function to Parse Text (ULTRA-ROBUST REGEX) ---
private fun parseTextForTotal(text: String): Double? {
    // EXPANDED REGEX 1: Accounts for garbled keywords (spaces, noise) and common abbreviations (TTL, TL, AMT, DUE, FUEL).
    // The keyword pattern now looks for a sequence of 1-4 letters/spaces matching common total keywords.
    val keywordPattern = "(T\\s*O\\s*T\\s*A\\s*L|T\\s*T\\s*L|T\\s*L|A\\s*M\\s*T|D\\s*U\\s*E|TOTAL|TTL|TL|AMT|DUE|AMOUNT DUE|BALANCE|PAY|FUEL)"

    // REGEX 1: Looks for keywords + amount (catches "FUEL TTL" and spaced keywords)
    val regex1 = Pattern.compile(
        """$keywordPattern\s*[: ]*\$?\s*(\d+[\s\\.]*\d{0,2})""", // Made decimals optional/flexible, allows spaces in number
        Pattern.CASE_INSENSITIVE
    )

    // REGEX 2: Looks for standalone currency format (catches "$ 35.70" or "USD 35.70" anywhere)
    val regex2 = Pattern.compile(
        """(USD|\$)\s*(\d+[\s\\.]*\d{0,2})""", // Catches $ 35.70, $35.7, USD 35.70, and spaced numbers
        Pattern.CASE_INSENSITIVE
    )

    val totals = mutableListOf<Double>()

    // Run Regex 1
    val matcher1 = regex1.matcher(text.replace(",", ""))
    while (matcher1.find()) {
        try {
            matcher1.group(2)?.let {
                // Sanitize the potential decimal separator spaces before parsing
                val cleanNumber = it.replace("\\s".toRegex(), "").replace(",", ".").toDoubleOrNull()
                cleanNumber?.let { totals.add(it) }
            }
        } catch (e: Exception) { /* Ignore */ }
    }

    // Run Regex 2
    val matcher2 = regex2.matcher(text.replace(",", ""))
    while (matcher2.find()) {
        try {
            matcher2.group(2)?.let {
                // Sanitize the number by removing spaces and replacing all dots/commas with a single dot.
                val cleanNumber = it.replace("\\s".toRegex(), "").replace(",", ".").toDoubleOrNull()
                cleanNumber?.let { totals.add(it) }
            }
        } catch (e: Exception) { /* Ignore */ }
    }


    // Return the largest total found, or null if none
    return totals.maxOrNull()
}


// --- The Main Screen Composable ---
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanTransactionScreen(
    viewModel: FinancialViewModel
) {
    // --- ADDED BACK HANDLER ---
    BackHandler {
        // Always return to the Dashboard when the user hits the Android back button
        viewModel.navigateToDashboard()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // --- State Variables (UPDATED) ---
    // Holds the final, confirmed result to show the dialog
    var confirmedScanResult by remember { mutableStateOf<ScanResult?>(null) }
    // A flag to stop the analyzer once we find a good number
    var isAnalyzerRunning by remember { mutableStateOf(true) }
    // State to track and control the flashlight
    var isTorchOn by remember { mutableStateOf(false) }
    val cameraReference = remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    // *** FIX: Removed 'private' modifier from local constant ***
    val MINIMUM_STABLE_FRAMES = 5 // Require 5 stable frames (approx. 250ms)
    val stableScanCounter = remember { mutableIntStateOf(0) }


    // Cleanup effect (Ensure torch is off on exit)
    DisposableEffect(lifecycleOwner) {
        onDispose {
            if (isTorchOn) {
                cameraReference.value?.cameraControl?.enableTorch(false)
                isTorchOn = false
            }
        }
    }


    // --- Permission Handling ---
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // This block runs when permission is granted
    if (cameraPermissionState.status == PermissionStatus.Granted) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- Camera Feed ---
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraExecutor = ContextCompat.getMainExecutor(ctx) // Use the main executor for everything

                    // 1. Set up the Text Recognizer
                    val textRecognizer =
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    // 2. Set up the Camera Analyzer
                    // Configuration includes TargetResolution workaround and ImageAnalysis fix
                    val analyzer = ImageAnalysis.Builder().apply {
                        setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                        setTargetResolution(android.util.Size(1280, 720)) // Use resolution instead of format
                    }.build() // Build here
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(
                                cameraExecutor,
                                MlKitAnalyzer(
                                    listOf(textRecognizer),

                                    // Use integer 0 for version 1.3.0-alpha05
                                    0,

                                    ContextCompat.getMainExecutor(ctx)
                                ) { result: MlKitAnalyzer.Result? ->
                                    // This block runs for every frame
                                    if (!isAnalyzerRunning) return@MlKitAnalyzer

                                    val fullText = result?.getValue(textRecognizer)?.text // Get the full detected text

                                    if (fullText != null) {
                                        // 1. Find the total using the robust regex
                                        val total = parseTextForTotal(fullText)

                                        if (total != null) {
                                            // Match found! Increment the stability counter.
                                            stableScanCounter.intValue++

                                            if (stableScanCounter.intValue >= MINIMUM_STABLE_FRAMES) {
                                                // The scan is stable. Lock the result and stop analyzing.

                                                // 2. Extract the first non-empty line as the raw store name
                                                val rawStoreName = fullText.lines().firstOrNull { it.isNotBlank() } ?: "Scanned Expense"

                                                // *** SMARTER NAME EXTRACTION LOGIC (PRESERVING INITIALS) ***
                                                var cleanName = rawStoreName

                                                // Step 1: Specific OCR Corrections (Fix 1/I confusion, often seen in JIFFY)
                                                cleanName = cleanName.replace("1FFY", "IFFY", ignoreCase = true)

                                                // Step 2: Aggressively remove only numbers, but leave punctuation (A.C. is protected).
                                                cleanName = cleanName.replace("[0-9]".toRegex(), " ")

                                                // Step 3: Remove isolated single letters or short number sequences
                                                cleanName = cleanName.replace("\\b\\d{1,5}\\b".toRegex(), "") // Removes unit numbers/dates
                                                cleanName = cleanName.replace("\\s[a-zA-Z]{1}\\s".toRegex(), " ").trim() // Remove isolated single letters

                                                // Step 4: Clean up excessive whitespace created by removal
                                                cleanName = cleanName.replace("\\s+".toRegex(), " ").trim()

                                                val finalStoreName = if (cleanName.length > 3) cleanName else rawStoreName
                                                // ****************************************

                                                // 3. Stop scanning and show confirmation
                                                isAnalyzerRunning = false
                                                confirmedScanResult = ScanResult(total, finalStoreName)
                                                stableScanCounter.intValue = 0 // Reset counter
                                            }
                                        } else {
                                            // No total found in this frame, reset the counter
                                            stableScanCounter.intValue = 0
                                        }
                                    } else {
                                        // No text detected in this frame, reset the counter
                                        stableScanCounter.intValue = 0
                                    }
                                }
                            )
                        }

                    // 3. Bind Camera to Lifecycle
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                        // Corrected typo
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()

                            // Capture the Camera object returned by bindToLifecycle
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                analyzer
                            )
                            // Store the camera reference
                            cameraReference.value = camera

                        } catch (exc: Exception) {
                            // AGGRESSIVE DEBUGGING
                            Log.e("CAMERA_BIND_ERROR", "Binding failed with exception:", exc)
                            Toast.makeText(ctx, "Camera error: ${exc.message}", Toast.LENGTH_LONG).show()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // --- UI Overlay ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top instruction text (REVERTED to single step)
                Text(
                    text = "Point camera at the receipt's total. Scanning automatically.",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                )

                // Middle guideline box
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1.6f) // ~Receipt shape
                        .border(2.dp, Color.Green, RoundedCornerShape(12.dp))
                )

                // Bottom button
                Button(
                    onClick = { viewModel.navigateToDashboard() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937))
                ) {
                    Text("Cancel")
                }
            }

            // --- Flashlight Toggle Button ---
            FloatingActionButton(
                onClick = {
                    isTorchOn = !isTorchOn
                    cameraReference.value?.cameraControl?.enableTorch(isTorchOn)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd) // Align to the bottom right
                    .padding(bottom = 96.dp, end = 16.dp), // Position it above the existing Cancel button
                containerColor = if (isTorchOn) Color(0xFF4ADE80) else Color.DarkGray
            ) {
                Icon(
                    if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Toggle Flashlight",
                    tint = if (isTorchOn) Color.Black else Color.White
                )
            }


            // --- Confirmation Dialog (ScanEditDialog) ---
            if (confirmedScanResult != null) {
                ScanEditDialog(
                    result = confirmedScanResult!!,
                    accounts = viewModel.accounts,
                    onDismiss = {
                        // User dismissed, reset and start scanning again
                        confirmedScanResult = null
                        isAnalyzerRunning = true
                    },
                    onConfirm = { description, amount, account, category ->
                        viewModel.addTransaction(
                            description = description,
                            amount = amount,
                            type = TransactionType.EXPENSE,
                            accountId = account.id,
                            date = Date(),
                            category = category,
                            isBill = false
                        )
                        viewModel.navigateToDashboard()
                    }
                )
            }
        }
    } else {
        // --- Permission Not Granted UI ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111827))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                "Scanning requires camera permission. Please grant it in settings."
            } else {
                "We need camera permission to scan receipts."
            }
            Text(textToShow, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Grant Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.navigateToDashboard() }) {
                Text("Back to Dashboard")
            }
        }
    }
}


// --- Scan Edit Dialog Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanEditDialog(
    result: ScanResult, // Accepts the found data
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (description: String, amount: Double, account: Account, category: Category) -> Unit
) {
    // State to toggle between the quick confirmation screen and the full edit screen
    var showQuickConfirm by remember { mutableStateOf(true) }

    // Default initial selections for a fast confirm.
    var selectedCategory by remember { mutableStateOf(Category.OTHER) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    // State for the full edit mode
    var descriptionEdit by remember { mutableStateOf(result.description) }
    var amountEdit by remember { mutableStateOf(result.amount.toString()) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val dialogContent = @Composable {
        if (showQuickConfirm) {
            // --- QUICK CONFIRMATION VIEW ---
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Did I read this correctly?", fontWeight = FontWeight.SemiBold)

                // Display Found Data
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF374151))) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(result.description, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(String.format("$%,.2f", result.amount), color = Color(0xFF4ADE80), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Action Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showQuickConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.weight(1f)
                    ) { Text("No, Edit") }

                    Button(
                        onClick = {
                            // Automatically confirm using found data and defaults
                            if (selectedAccount == null) {
                                Toast.makeText(context, "Please select an account first.", Toast.LENGTH_SHORT).show()
                            } else {
                                onConfirm(result.description, result.amount, selectedAccount!!, selectedCategory)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
                        modifier = Modifier.weight(1f)
                    ) { Text("Yes, Save") }
                }
            }
        } else {
            // --- FULL EDIT VIEW (RECYCLED OLD CODE) ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = descriptionEdit,
                    onValueChange = { descriptionEdit = it },
                    label = { Text("Description") }
                )
                OutlinedTextField(
                    value = amountEdit,
                    onValueChange = { amountEdit = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Account Dropdown
                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = !accountMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccount?.let { "${it.name} (${it.type})" } ?: "Select Account",
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
                                text = { Text("${account.name} (${account.type})", color = Color.White) },
                                onClick = {
                                    selectedAccount = account
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category Dropdown
                CategorySelectorDropdown(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )

                // Save Button for Edit Mode
                Button(
                    onClick = {
                        val finalAmount = amountEdit.toDoubleOrNull()
                        if (finalAmount == null) {
                            Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                        } else if (selectedAccount == null) {
                            Toast.makeText(context, "Please select an account", Toast.LENGTH_SHORT).show()
                        } else {
                            onConfirm(descriptionEdit, finalAmount, selectedAccount!!, selectedCategory)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Edited Transaction")
                }
            }
        }
    }

    // The main AlertDialog wrapper
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showQuickConfirm) "Scan Result Found" else "Edit Scan Details") },
        text = dialogContent,
        confirmButton = {}, // Confirmation handled inside dialogContent
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}