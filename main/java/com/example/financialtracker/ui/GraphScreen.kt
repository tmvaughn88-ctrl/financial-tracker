// ui/GraphScreen.kt

package com.example.financialtracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financialtracker.FinancialViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(viewModel: FinancialViewModel) {
    // Allows user to use the Android back button to return to the Dashboard
    BackHandler { viewModel.navigateToDashboard() }

    // FIX: Using viewModel.monthlySpendingSegments ensures we have the chart data
    val segments = viewModel.monthlySpendingSegments
    val totalSpending = segments.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Charts & Trends") },
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
        containerColor = Color(0xFF111827) // Dark background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    "Monthly Spending Breakdown",
                    color = Color.LightGray,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                Text(
                    "Total Expenses: ${NumberFormat.getCurrencyInstance(Locale.US).format(totalSpending)}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            item {
                // --- CHART DISPLAY AREA ---
                if (segments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No budget expenses recorded this month.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    PieChart(
                        segments = segments,
                        total = totalSpending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(vertical = 16.dp)
                    )
                }
            }

            item {
                // *** FIX APPLIED HERE ***
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 16.dp))
                // ************************
                Text("Category Legend", color = Color.LightGray, fontWeight = FontWeight.SemiBold)
            }

            items(segments) { segment ->
                LegendRow(segment = segment, total = totalSpending)
            }
        }
    }
}

// --- PIE CHART COMPOSABLES ---

@Composable
fun PieChart(
    segments: List<FinancialViewModel.PieChartSegment>,
    total: Double,
    modifier: Modifier = Modifier
) {
    // Animation for visual effect
    val sweepAngle by animateFloatAsState(
        targetValue = 360f,
        label = "pieChartAnimation"
    )

    Canvas(modifier = modifier) {
        val diameter = size.minDimension * 0.8f
        val radius = diameter / 2
        val topLeft = Offset(
            (size.width - diameter) / 2,
            (size.height - diameter) / 2
        )
        val area = Size(diameter, diameter)

        var startAngle = 0f

        segments.forEach { segment ->
            val angle = (segment.amount / total).toFloat() * sweepAngle

            drawArc(
                color = segment.color,
                startAngle = startAngle,
                sweepAngle = angle,
                useCenter = true,
                topLeft = topLeft,
                size = area
            )
            startAngle += angle
        }
    }
}

@Composable
fun LegendRow(segment: FinancialViewModel.PieChartSegment, total: Double) {
    val percentage = (segment.amount / total) * 100
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(segment.color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                segment.category.displayName,
                color = Color.White,
                fontSize = 16.sp
            )
        }
        Text(
            // Ensure we call format with the correct double type
            "${currencyFormat.format(segment.amount)} (${String.format("%.1f", percentage)}%)",
            color = Color.LightGray,
            fontWeight = FontWeight.Medium
        )
    }
}