// ui/ScanBillScreen.kt

package com.example.financialtracker.ui

import android.Manifest
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import com.example.financialtracker.FinancialViewModel
import com.example.financialtracker.UpcomingDisplayItem
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

// --- Data Class for Scan Result (Amount and Date) ---
data class BillScanResult(val amount: Double, val date: Date)
// ----------------------------------------------------

// --- Helper Function to Parse Text ---
private fun parseTextForBill(text: String): BillScanResult? {
    // 1. Amount Extraction (Look for TOTAL, AMOUNT, DUE)
    val keywordPattern = "(TOTAL|TTL|DUE|AMOUNT DUE|BALANCE|PAY|BILLED|CHARGE|FEES)"
    val amountRegex = Pattern.compile(
        """$keywordPattern\s*[: ]*\$?\s*(\d+[\s\\.]*\d{2})""",
        Pattern.CASE_INSENSITIVE
    )

    var parsedAmount: Double? = null
    val cleanedText = text.replace(",", "")

    amountRegex.matcher(cleanedText).results().forEach { matchResult ->
        // Group 2 contains the number part
        val textMatch = matchResult.group(2)?.replace("\\s".toRegex(), "")

        try {
            parsedAmount = textMatch?.toDoubleOrNull()
            if (parsedAmount != null && parsedAmount!! > 0) {
                return@forEach
            }
        } catch (e: Exception) { /* Ignore */ }
    }

    if (parsedAmount == null) {
        Log.d("ScanBill", "Keyword amount failed.")
        return null
    }


    // 2. Date Extraction
    val datePatterns = listOf(
        Pair(Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"), SimpleDateFormat("MM/dd/yyyy", Locale.US)),
        Pair(Pattern.compile("(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s*(\\d{1,2}),?\\s*(\\d{4})", Pattern.CASE_INSENSITIVE), SimpleDateFormat("MMM d, yyyy", Locale.US)),
        Pair(Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})"), SimpleDateFormat("yyyy/MM/dd", Locale.US))
    )

    var parsedDate: Date? = null
    for ((pattern, format) in datePatterns) {
        pattern.matcher(text).results().findFirst().ifPresent { matchResult ->
            try {
                val dateString = matchResult.group(0).trim()
                format.isLenient = false
                parsedDate = format.parse(dateString)
                return@ifPresent
            } catch (e: Exception) {
                Log.e("ScanBill", "Failed to parse date string: ${matchResult.group(0)} with format: ${format.toPattern()}", e)
            }
        }
        if (parsedDate != null) break
    }

    if (parsedDate == null) {
        Log.d("ScanBill", "Date extraction failed.")
        return null
    }

    val maxFuture = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.time
    val minPast = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }.time
    if (parsedDate!!.after(maxFuture) || parsedDate!!.before(minPast)) {
        Log.d("ScanBill", "Date is outside reasonable range: $parsedDate")
        return null
    }

    return BillScanResult(parsedAmount!!, parsedDate!!)
}
// -------------------------------------------------------------------------


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanBillScreen(
    viewModel: FinancialViewModel,
    upcomingItem: UpcomingDisplayItem // Target item is passed here
) {
    // --- State Variables ---
    var confirmedScanResult by remember { mutableStateOf<BillScanResult?>(null) }
    var isAnalyzerRunning by remember { mutableStateOf(true) }
    var isTorchOn by remember { mutableStateOf(false) }
    val cameraReference = remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val MINIMUM_STABLE_FRAMES = 5
    val stableScanCounter = remember { mutableIntStateOf(0) }

    // Local context and lifecycle
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

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

    // Handle Back Navigation
    BackHandler {
        // Clear the item in action when returning from the scanner
        viewModel.clearUpcomingItemForAction()
        viewModel.navigateToDashboard()
    }

    if (cameraPermissionState.status == PermissionStatus.Granted) {
        Box(modifier = Modifier.fillMaxSize()) {

            // --- Camera Feed (Same as ScanTransactionScreen) ---
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraExecutor = ContextCompat.getMainExecutor(ctx)
                    val textRecognizer =
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    val analyzer = ImageAnalysis.Builder().apply {
                        setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                        setTargetResolution(android.util.Size(1280, 720))
                    }.build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(
                                cameraExecutor,
                                MlKitAnalyzer(
                                    listOf(textRecognizer),
                                    0,
                                    ContextCompat.getMainExecutor(ctx)
                                ) { result: MlKitAnalyzer.Result? ->
                                    // --- ANALYSIS LOGIC ---
                                    if (!isAnalyzerRunning) return@MlKitAnalyzer

                                    val fullText = result?.getValue(textRecognizer)?.text

                                    if (fullText != null) {
                                        val billResult = parseTextForBill(fullText)

                                        if (billResult != null) {
                                            stableScanCounter.intValue++

                                            if (stableScanCounter.intValue >= MINIMUM_STABLE_FRAMES) {
                                                isAnalyzerRunning = false
                                                confirmedScanResult = billResult
                                                stableScanCounter.intValue = 0
                                            }
                                        } else {
                                            stableScanCounter.intValue = 0
                                        }
                                    } else {
                                        stableScanCounter.intValue = 0
                                    }
                                    // --- END ANALYSIS LOGIC ---
                                }
                            )
                        }

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()

                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                analyzer
                            )
                            cameraReference.value = camera

                        } catch (exc: Exception) {
                            Log.e("CAMERA_BIND_ERROR", "Binding failed:", exc)
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
                Text(
                    text = "Scan the final amount and due date for: ${upcomingItem.description}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1.6f)
                        .border(2.dp, Color.Yellow, RoundedCornerShape(12.dp))
                )

                Button(
                    onClick = {
                        // Clear item in action when canceling scan
                        viewModel.clearUpcomingItemForAction()
                        viewModel.navigateToDashboard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937))
                ) {
                    Text("Cancel Scan")
                }
            }

            // --- Flashlight Toggle Button ---
            FloatingActionButton(
                onClick = {
                    isTorchOn = !isTorchOn
                    cameraReference.value?.cameraControl?.enableTorch(isTorchOn)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 96.dp, end = 16.dp),
                containerColor = if (isTorchOn) Color(0xFF4ADE80) else Color.DarkGray
            ) {
                Icon(
                    if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Toggle Flashlight",
                    tint = if (isTorchOn) Color.Black else Color.White
                )
            }


            // --- Confirmation Dialog ---
            if (confirmedScanResult != null) {
                BillScanConfirmDialog(
                    item = upcomingItem,
                    result = confirmedScanResult!!,
                    onDismiss = {
                        confirmedScanResult = null
                        isAnalyzerRunning = true
                    },
                    onConfirm = { amount, date ->
                        viewModel.confirmRecurringItemByScan(
                            originalId = upcomingItem.originalId,
                            newAmount = amount,
                            confirmedDate = date
                        )
                        viewModel.navigateToDashboard()
                    }
                )
            }
        }
    } else {
        // --- Permission Not Granted UI (Same as ScanTransactionScreen) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111827))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                "Bill scanning requires camera permission. Please grant it in settings."
            } else {
                "We need camera permission to scan your bills."
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


// --- Bill Scan Confirmation Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillScanConfirmDialog(
    item: UpcomingDisplayItem,
    result: BillScanResult,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, date: Date) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // State for user edits
    var amountEdit by remember { mutableStateOf(result.amount.toString()) }
    var selectedDate by remember { mutableStateOf(result.date) }
    var showDatePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // DatePickerState initialization needs to be aware of the date's time zone offset
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = result.date.time + TimeZone.getDefault().getOffset(result.date.time)
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
                            // Corrected arithmetic to ensure Long types are used
                            selectedDate = Date(millis - offset.toLong())
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Bill Scan: ${item.description}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Please confirm the scanned details below.", fontWeight = FontWeight.SemiBold)

                // Editable Amount
                OutlinedTextField(
                    value = amountEdit,
                    onValueChange = {
                        // Basic validation: only allow digits and one decimal point
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amountEdit = it
                        }
                    },
                    label = { Text("Confirmed Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Editable Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Confirmed Due Date:")
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text(dateFormat.format(selectedDate))
                    }
                }

                Text(
                    "Original Estimated Amount: $${String.format("%,.2f", item.amount)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAmount = amountEdit.toDoubleOrNull()
                    if (finalAmount == null || finalAmount <= 0) {
                        Toast.makeText(context, "Invalid amount.", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(finalAmount, selectedDate)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80))
            ) { Text("Confirm & Save Bill") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}