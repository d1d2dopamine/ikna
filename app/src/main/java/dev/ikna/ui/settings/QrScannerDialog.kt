package dev.ikna.ui.settings

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import dev.ikna.sync.IknaPixelEmblem
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.iknaTileShape

/**
 * Full-screen in-app Ikna QR scanner. Never opens an external browser or leaves Ikna:
 * as soon as a valid Ikna pairing QR is seen, it immediately hands the payload to the
 * pairing controller and closes.
 */
@Composable
fun IknaQrScannerDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchOn by remember { mutableStateOf(false) }
    var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    var delivered by remember { mutableStateOf(false) }
    var wrongQrHint by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, scannerView) {
        val view = scannerView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.resume()
                Lifecycle.Event.ON_PAUSE -> view?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.pause()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val accent = MaterialTheme.colorScheme.primary
        val tileShape = iknaTileShape()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    DecoratedBarcodeView(ctx).apply {
                        statusView?.visibility = View.GONE
                        viewFinder?.visibility = View.GONE
                        barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult?) {
                                if (delivered) return
                                val text = result?.text?.trim().orEmpty()
                                if (text.isEmpty()) return
                                val looksLikeIkna = text.startsWith("ikna-pair:") ||
                                    (text.startsWith("{") && text.contains("\"certificate\""))
                                if (looksLikeIkna) {
                                    delivered = true
                                    pause()
                                    onScanned(text)
                                } else {
                                    wrongQrHint = true
                                }
                            }
                        })
                        resume()
                        scannerView = this
                    }
                }
            )

            // Top bar with Ikna logo lockup and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.58f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IknaPixelEmblem(
                        modifier = Modifier.size(width = 20.dp, height = 23.dp),
                        inkColor = Color.White,
                        accentColor = accent
                    )
                    Text(
                        text = "ikna",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.4).sp
                        ),
                        color = Color.White
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = if (torchOn) 0.24f else 0.12f), tileShape)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), tileShape)
                            .clickable {
                                torchOn = !torchOn
                                if (torchOn) scannerView?.setTorchOn() else scannerView?.setTorchOff()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = S.t(if (torchOn) "sync.torch_off" else "sync.torch_on"),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), tileShape)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), tileShape)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = S.t("sync.close_camera"),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Crisp Ikna corner-bracket viewfinder in the center
            Canvas(
                modifier = Modifier
                    .size(252.dp)
                    .align(Alignment.Center)
            ) {
                val stroke = 4.dp.toPx()
                val arm = 34.dp.toPx()
                val w = size.width
                val h = size.height
                val frameColor = if (wrongQrHint) Color(0xFFFFB74D) else accent
                // Top-left
                drawLine(frameColor, Offset(0f, 0f), Offset(arm, 0f), stroke, StrokeCap.Round)
                drawLine(frameColor, Offset(0f, 0f), Offset(0f, arm), stroke, StrokeCap.Round)
                // Top-right
                drawLine(frameColor, Offset(w, 0f), Offset(w - arm, 0f), stroke, StrokeCap.Round)
                drawLine(frameColor, Offset(w, 0f), Offset(w, arm), stroke, StrokeCap.Round)
                // Bottom-left
                drawLine(frameColor, Offset(0f, h), Offset(arm, h), stroke, StrokeCap.Round)
                drawLine(frameColor, Offset(0f, h), Offset(0f, h - arm), stroke, StrokeCap.Round)
                // Bottom-right
                drawLine(frameColor, Offset(w, h), Offset(w - arm, h), stroke, StrokeCap.Round)
                drawLine(frameColor, Offset(w, h), Offset(w, h - arm), stroke, StrokeCap.Round)
            }

            // Bottom instruction strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.64f))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = S.t(if (wrongQrHint) "sync.scan_not_ikna" else "sync.scan_prompt"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (wrongQrHint) Color(0xFFFFCC80) else Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
