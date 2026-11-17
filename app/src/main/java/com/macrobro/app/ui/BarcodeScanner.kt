package com.macrobro.app.ui

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google's code scanner: a full-screen camera scanner provided by Play services.
 * The app needs no camera permission, and the scanner never shares images with the app.
 */
object BarcodeScanner {
    private const val NO_PLAY_SERVICES =
        "Barcode scanning needs Google Play services. You can type the barcode instead."

    fun scan(context: Context, onCode: (String) -> Unit, onMessage: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build()
        val scanner = try {
            GmsBarcodeScanning.getClient(context, options)
        } catch (e: Exception) {
            onMessage(NO_PLAY_SERVICES)
            return
        }
        val installer = ModuleInstall.getClient(context)
        installer.areModulesAvailable(scanner)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val raw = barcode.rawValue
                            if (raw.isNullOrBlank()) onMessage("Couldn't read that barcode. Try again.")
                            else onCode(raw)
                        }
                        .addOnFailureListener { e -> onMessage("Scanner error: ${e.message ?: "unknown"}") }
                } else {
                    onMessage("Getting the barcode scanner ready (one-time download). Try again in a few seconds.")
                    installer.installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
                        .addOnFailureListener { e ->
                            onMessage("Couldn't download the scanner: ${e.message ?: "unknown error"}")
                        }
                }
            }
            .addOnFailureListener { onMessage(NO_PLAY_SERVICES) }
    }
}
