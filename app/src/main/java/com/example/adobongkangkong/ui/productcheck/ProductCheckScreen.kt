package com.example.adobongkangkong.ui.productcheck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.productcheck.EvaluateProductNutritionUseCase
import com.example.adobongkangkong.ui.food.editor.BarcodeScannerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCheckScreen(
    onBack: () -> Unit,
    onImportFoodWithBarcode: (String) -> Unit,
    vm: ProductCheckViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    if (state.isScannerOpen) {
        ModalBottomSheet(
            onDismissRequest = vm::closeScanner
        ) {
            BarcodeScannerSheet(
                onClose = vm::closeScanner,
                onBarcode = vm::onBarcodeScanned
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Check") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Scan a product barcode to check sodium and total sugars using USDA data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = vm::openScanner,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text("Scan product")
            }

            Spacer(Modifier.height(16.dp))

            if (state.isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Checking USDA product…")
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.evaluation?.let { evaluation ->
                ProductCheckResultCard(
                    evaluation = evaluation,
                    barcode = state.barcode,
                    onScanAnother = vm::clearResult,
                    onImportFoodWithBarcode = onImportFoodWithBarcode
                )
            }
        }
    }
}

@Composable
private fun ProductCheckResultCard(
    evaluation: EvaluateProductNutritionUseCase.Result,
    barcode: String?,
    onScanAnother: () -> Unit,
    onImportFoodWithBarcode: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = evaluation.foodName,
                style = MaterialTheme.typography.titleLarge
            )

            evaluation.brand?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            evaluation.servingText?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Serving: $it",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            evaluation.nutrients.forEach { nutrient ->
                Text(
                    text = "${nutrient.name}: ${nutrient.value?.clean() ?: "—"} ${nutrient.unit}",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = nutrient.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nutrient.isWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(Modifier.height(10.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Overall: ${evaluation.overallMessage}",
                style = MaterialTheme.typography.titleMedium,
                color = if (evaluation.hasWarnings) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onScanAnother,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan another")
            }

            if (!barcode.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onImportFoodWithBarcode(barcode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import food")
                }
            }
        }
    }
}

private fun Double.clean(): String =
    if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        "%,.2f".format(this).trimEnd('0').trimEnd('.')
    }
