package com.ultraprocessed.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.theme.Tokens
import com.ultraprocessed.ui.MainViewModel
import com.ultraprocessed.ui.PendingResult
import com.ultraprocessed.ui.components.Overline
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ScanScreen(
    mainVm: MainViewModel,
    onResult: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scanVm: ScanViewModel = viewModel(factory = ScanViewModel.Factory)
    val state by scanVm.scanState.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(scanVm) {
        scanVm.pipeline.barcodes.collectLatest { code -> scanVm.onBarcode(code) }
    }

    LaunchedEffect(state) {
        val done = state as? ScanState.Done ?: return@LaunchedEffect
        mainVm.publish(
            PendingResult(
                analysis = done.analysis,
                source = done.source,
                barcode = done.barcode,
                imageBytes = done.imageBytes,
                imageUrl = done.imageUrl
            )
        )
        scanVm.reset()
        onResult()
    }

    Box(modifier = Modifier.fillMaxSize().background(Semantic.colors.bg)) {

        if (hasCameraPermission) {
            CameraSurface(scanVm)
        } else {
            PermissionPrompt(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        TopChrome(
            onOpenSettings = onOpenSettings,
            onOpenHistory = onOpenHistory
        )

        BottomChrome(
            state = state,
            onShutter = { scanVm.onShutter() }
        )
    }
}

@Composable
private fun CameraSurface(vm: ScanViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    LaunchedEffect(previewView) {
        vm.pipeline.setSurfaceProvider(previewView.surfaceProvider)
        vm.pipeline.bind(lifecycleOwner)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Reticle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Tokens.Space.s7,
                    vertical = Tokens.Space.s9
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(Tokens.Radius.lg))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(Tokens.Radius.lg)
                    )
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Tokens.Space.s5),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Camera access needed to scan food",
                color = Semantic.colors.inkHigh,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(Tokens.Space.s4))
            Text(
                text = "Tap to grant",
                color = Semantic.colors.accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onRequest() }
            )
        }
    }
}

@Composable
private fun BoxScope.TopChrome(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(
                start = Tokens.Space.s5,
                end = Tokens.Space.s5,
                top = Tokens.Space.s7
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Overline(text = "Ultraprocessed", color = Color.White.copy(alpha = 0.85f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(icon = Icons.Default.History, onClick = onOpenHistory)
            Spacer(Modifier.size(Tokens.Space.s2))
            CircleIconButton(icon = Icons.Default.Settings, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White
        )
    }
}

@Composable
private fun BoxScope.BottomChrome(
    state: ScanState,
    onShutter: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = Tokens.Space.s7),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusStrip(state = state)
        Spacer(Modifier.height(Tokens.Space.s5))
        ShutterButton(onClick = onShutter, busy = state is ScanState.Looking)
    }
}

@Composable
private fun StatusStrip(state: ScanState) {
    val text = when (state) {
        is ScanState.Idle -> "Scanning for barcode..."
        is ScanState.Looking -> state.message
        is ScanState.Done -> "Done"
        is ScanState.Error -> state.message
    }
    Box(
        modifier = Modifier
            .padding(horizontal = Tokens.Space.s5)
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (state is ScanState.Error) Semantic.colors.error else Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit, busy: Boolean) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (busy) Color.LightGray else Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.85f), CircleShape)
        )
    }
}
