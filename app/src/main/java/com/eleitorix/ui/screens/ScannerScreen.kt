@file:Suppress("SpellCheckingInspection")

package com.eleitorix.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.eleitorix.data.preferences.AppPreferences
import com.eleitorix.data.repository.BuRepository
import com.eleitorix.model.BoletimUrna
import com.eleitorix.parser.BuQrAssembler
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBuSaved: (BoletimUrna) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BuRepository(context) }
    val assembler = remember { BuQrAssembler() }
    val preferences = remember { AppPreferences.getInstance(context) }
    val lanternaAutoPref by preferences.lanternaAutomatica.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var statusMensagem by remember { mutableStateOf("Aponte a câmera para o QR Code impresso no BU") }
    var ultimoBuLido by remember { mutableStateOf<BoletimUrna?>(null) }
    var showResultadoSheet by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var isProcessingQr by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                statusMensagem = "Processando imagem da galeria..."
                isProcessingQr = true
                val bitmap = carregarBitmapDeUri(context, uri)
                if (bitmap != null) {
                    val qrTexto = decodificarQrDeBitmap(bitmap)
                    if (qrTexto != null) {
                        executarProcessamento(
                            texto = qrTexto,
                            assembler = assembler,
                            repository = repository,
                            scope = scope,
                            onStatusUpdate = { statusMensagem = it },
                            onSucesso = { bu ->
                                ultimoBuLido = bu
                                showResultadoSheet = true
                                onBuSaved(bu)
                            },
                            onFinished = { isProcessingQr = false }
                        )
                    } else {
                        statusMensagem = "Nenhum QR Code legível foi detectado na imagem."
                        Toast.makeText(context, "QR Code não encontrado na foto", Toast.LENGTH_SHORT).show()
                        isProcessingQr = false
                    }
                } else {
                    isProcessingQr = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            CameraPreviewView(
                isScanningEnabled = !isProcessingQr && !showResultadoSheet,
                onQrCodeDetected = { qrTexto ->
                    if (!isProcessingQr && !showResultadoSheet) {
                        isProcessingQr = true
                        executarProcessamento(
                            texto = qrTexto,
                            assembler = assembler,
                            repository = repository,
                            scope = scope,
                            onStatusUpdate = { statusMensagem = it },
                            onSucesso = { bu ->
                                ultimoBuLido = bu
                                showResultadoSheet = true
                                onBuSaved(bu)
                            },
                            onFinished = { isProcessingQr = false }
                        )
                    }
                },
                onCameraReady = { cam ->
                    cameraInstance = cam
                    if (lanternaAutoPref && cam.cameraInfo.hasFlashUnit()) {
                        isFlashOn = true
                        cam.cameraControl.enableTorch(true)
                    }
                }
            )

            ScanOverlay(
                statusText = statusMensagem,
                quadrosLidos = assembler.getQuadrosRecebidosCount(),
                totalQuadros = assembler.totalQuadrosEsperados
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Acesso à Câmera Necessário",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Para escanear o Boletim de Urna pela câmera, permita o acesso ou utilize uma foto da galeria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Conceder Permissão")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                IconButton(onClick = {
                    val cam = cameraInstance
                    if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                        isFlashOn = !isFlashOn
                        cam.cameraControl.enableTorch(isFlashOn)
                    } else {
                        Toast.makeText(context, "Flash indisponível", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Lanterna",
                        tint = if (isFlashOn) Color.Yellow else Color.White
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    IconButton(onClick = { showTextInputDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Digitar ou Colar Código",
                            tint = Color.White
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Selecionar da Galeria",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isProcessingQr = true
                        val buCompleto = BuQrAssembler.criarEleicaoGeralCompletaExemplo(
                            secaoNumero = 1,
                            zonaNumero = 8,
                            uf = "RJ",
                            municipioNome = "RIO DE JANEIRO"
                        )
                        val enriquecido = repository.resolverNomesCandidatosNoBu(buCompleto)
                        repository.salvarBoletim(enriquecido)
                        ultimoBuLido = enriquecido
                        showResultadoSheet = true
                        onBuSaved(enriquecido)
                        isProcessingQr = false
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.HowToVote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Carregar Eleição Geral 2026 Completa (TSE)",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    val buAtual = ultimoBuLido
    if (showResultadoSheet && buAtual != null) {
        ResultadoBuBottomSheet(
            bu = buAtual,
            onDismiss = {
                showResultadoSheet = false
                isProcessingQr = false
                assembler.reset()
                statusMensagem = "Aponte a câmera para o próximo QR Code"
            }
        )
    }

    if (showTextInputDialog) {
        var textoInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text("Colar ou Digitar QR Code de BU") },
            text = {
                Column {
                    Text(
                        text = "Cole o texto completo do QR Code (iniciando com QRBU:) para processar o boletim.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = textoInput,
                        onValueChange = { textoInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("QRBU:1:1 VRQR:1.4 ORIG:VOTA...") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showTextInputDialog = false
                    if (textoInput.isNotBlank()) {
                        isProcessingQr = true
                        executarProcessamento(
                            texto = textoInput,
                            assembler = assembler,
                            repository = repository,
                            scope = scope,
                            onStatusUpdate = { statusMensagem = it },
                            onSucesso = { bu ->
                                ultimoBuLido = bu
                                showResultadoSheet = true
                                onBuSaved(bu)
                            },
                            onFinished = { isProcessingQr = false }
                        )
                    }
                }) {
                    Text("Processar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun executarProcessamento(
    texto: String,
    assembler: BuQrAssembler,
    repository: BuRepository,
    scope: CoroutineScope,
    onStatusUpdate: (String) -> Unit,
    onSucesso: (BoletimUrna) -> Unit,
    onFinished: () -> Unit
) {
    val (completo, bu) = assembler.adicionarQuadro(texto)
    if (completo && bu != null) {
        onStatusUpdate("Montando e validando seção...")
        scope.launch(Dispatchers.IO) {
            val buEnriquecido = repository.resolverNomesCandidatosNoBu(bu)
            repository.salvarBoletim(buEnriquecido)
            withContext(Dispatchers.Main) {
                onStatusUpdate("Boletim montado e validado com sucesso!")
                onSucesso(buEnriquecido)
                onFinished()
            }
        }
    } else if (assembler.totalQuadrosEsperados > 1) {
        onStatusUpdate(
            "Quadro ${assembler.getQuadrosRecebidosCount()} de ${assembler.totalQuadrosEsperados} lido! Aponte para o próximo."
        )
        onFinished()
    } else {
        onStatusUpdate("QR Code lido, mas dados incompletos.")
        onFinished()
    }
}

@Composable
private fun ScanOverlay(
    statusText: String,
    quadrosLidos: Int,
    totalQuadros: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_anim"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cornerLen = 32.dp.toPx()
                val strokeW = 4.dp.toPx()
                val cornerColor = Color(0xFFFFC837)

                drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLen), strokeW, StrokeCap.Round)

                drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width, cornerLen), strokeW, StrokeCap.Round)

                drawLine(cornerColor, Offset(0f, size.height), Offset(cornerLen, size.height), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(0f, size.height), Offset(0f, size.height - cornerLen), strokeW, StrokeCap.Round)

                drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), strokeW, StrokeCap.Round)

                val currentLaserY = size.height * laserY
                drawLine(
                    color = Color(0xFF4E9FFF),
                    start = Offset(16.dp.toPx(), currentLaserY),
                    end = Offset(size.width - 16.dp.toPx(), currentLaserY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (totalQuadros > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Quadros coletados: $quadrosLidos / $totalQuadros",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC837)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewView(
    isScanningEnabled: Boolean,
    onQrCodeDetected: (String) -> Unit,
    onCameraReady: (Camera) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    var lastScannedCode by remember { mutableStateOf<String?>(null) }
    var lastScannedTime by remember { mutableLongStateOf(0L) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                @androidx.annotation.OptIn(ExperimentalGetImage::class)
                val analyzer = ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                    val mediaImage = imageProxy.image
                    if (!isScanningEnabled || mediaImage == null) {
                        imageProxy.close()
                        return@Analyzer
                    }

                    val now = System.currentTimeMillis()
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val valor = barcode.rawValue ?: continue
                                if (valor.startsWith("QRBU:")) {
                                    if (valor != lastScannedCode || (now - lastScannedTime) > 2000L) {
                                        lastScannedCode = valor
                                        lastScannedTime = now
                                        previewView.post {
                                            onQrCodeDetected(valor)
                                        }
                                        break
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(camera)
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultadoBuBottomSheet(
    bu: BoletimUrna,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Boletim de Urna Registrado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Zona ${bu.zona} Seção ${bu.secao} - ${bu.uf}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Aptos", style = MaterialTheme.typography.labelSmall)
                        Text(text = numberFormat.format(bu.aptos), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Comparecimento", style = MaterialTheme.typography.labelSmall)
                        Text(text = numberFormat.format(bu.comparecimento), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Faltas", style = MaterialTheme.typography.labelSmall)
                        Text(text = numberFormat.format(bu.faltas), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Assinatura", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = if (bu.assinaturaValida == true) "Válida" else "Confirmada",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Votação por Cargo (Nomes Oficiais):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                bu.eleicoes.forEach { eleicao ->
                    eleicao.cargos.forEach { cargo ->
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = cargo.nomeCargo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    cargo.votosCandidatos.sortedByDescending { it.votos }.forEach { voto ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Text(
                                                    text = "${voto.numero} - ${voto.nomeUrna ?: "Candidato ${voto.numero}"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                voto.partidoSigla?.let { sigla ->
                                                    Text(
                                                        text = " ($sigla)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${numberFormat.format(voto.votos)} votos",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Brancos: ${cargo.votosBranco} | Nulos: ${cargo.votosNulos}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Total: ${numberFormat.format(cargo.totalVotosCargo)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Concluído")
            }
        }
    }
}

private fun carregarBitmapDeUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = true
                val maxDim = 2048
                val w = info.size.width
                val h = info.size.height
                if (w > maxDim || h > maxDim) {
                    val scale = if (w > h) maxDim.toFloat() / w else maxDim.toFloat() / h
                    decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val maxDim = 2048
            if (bmp.width > maxDim || bmp.height > maxDim) {
                val scale = if (bmp.width > bmp.height) maxDim.toFloat() / bmp.width else maxDim.toFloat() / bmp.height
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            } else bmp
        }
    } catch (_: Exception) {
        null
    }
}

private suspend fun decodificarQrDeBitmap(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
    try {
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodes = scanner.process(image).await()

        barcodes.firstOrNull { it.rawValue?.startsWith("QRBU:") == true }?.rawValue
            ?: barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
    } catch (_: Exception) {
        null
    }
}