package com.prism.gallery.ui.vault

import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.gallery.data.vault.VaultAuth
import com.prism.gallery.data.vault.VaultCrypto
import com.prism.gallery.data.vault.VaultMode
import com.prism.gallery.data.vault.VaultSession
import com.prism.gallery.ui.components.GlassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(val auth: VaultAuth, val crypto: VaultCrypto, val session: VaultSession) : ViewModel() {
    val configured = MutableStateFlow<Boolean?>(null)
    val files = MutableStateFlow<List<File>>(emptyList())
    val error = MutableStateFlow("")
    init { viewModelScope.launch { configured.value = auth.isConfigured() } }
    val busy = MutableStateFlow(false)
    fun setup(pin: String, decoy: String) = viewModelScope.launch {
        busy.value = true
        runCatching { auth.setup(pin, decoy) }.onFailure { error.value = "Could not create vault" }.onSuccess { configured.value = true }
        busy.value = false
    }
    fun attempt(pin: String) = viewModelScope.launch {
        // TODO(break-in selfie): capture a front-camera frame with CameraX and store it encrypted in the vault.
        busy.value = true
        val m = runCatching { auth.attempt(pin) { error.value = "Too many attempts" } }.getOrNull()
        busy.value = false
        if (m == null) error.value = "Wrong PIN" else { error.value = ""; reload() }
    }
    fun reload() { viewModelScope.launch { files.value = withContext(Dispatchers.IO) { if (session.mode.value == VaultMode.REAL) crypto.list() else emptyList() } } }
    fun shred(f: File) { viewModelScope.launch { withContext(Dispatchers.IO) { crypto.shred(f) }; reload() } }
    fun add(ctx: android.content.Context, uris: List<android.net.Uri>) { viewModelScope.launch(Dispatchers.IO) {
        uris.forEach { u -> runCatching { ctx.contentResolver.openInputStream(u)?.use { crypto.encrypt(it, if (ctx.contentResolver.getType(u)?.startsWith("video") == true) "mp4" else "jpg") } } }
        reload()
    } }
    suspend fun thumb(f: File) = withContext(Dispatchers.IO) {
        runCatching { crypto.decrypt(f).let { b -> BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = 4 }) } }.getOrNull()
    }
}

@Composable
fun PrivateScreen(vm: VaultViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val act = ctx as FragmentActivity
    val mode by vm.session.mode.collectAsState()
    val configured by vm.configured.collectAsState()
    val files by vm.files.collectAsState()
    val error by vm.error.collectAsState()
    var pin by remember { mutableStateOf("") }
    var decoy by remember { mutableStateOf("") }

    // Block screenshots / recents thumbnails while this tab is visible.
    DisposableEffect(mode) {
        if (mode != null) act.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { act.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.add(ctx, uris)
    }

    val busy by vm.busy.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Private", style = MaterialTheme.typography.headlineSmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            configured == null -> {}
            mode != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Text("Add to vault") }
                    TextButton({ vm.session.lock() }) { Text("Lock now") }
                }
                LaunchedEffect(mode) { vm.reload() }
                LazyVerticalGrid(GridCells.Adaptive(130.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(files, key = { it.name }) { f ->
                        val bmp by produceState<android.graphics.Bitmap?>(null, f) { value = vm.thumb(f) }
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                            bmp?.let { Image(it.asImageBitmap(), "Private item", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            TextButton({ vm.shred(f) }, Modifier.align(Alignment.BottomEnd)) { Text("Shred") }
                        }
                    }
                }
            }
            configured == false -> GlassSurface(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create a PIN or password for your private space")
                    OutlinedTextField(pin, { pin = it }, label = { Text("PIN / password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(decoy, { decoy = it }, label = { Text("Decoy PIN (optional)") }, visualTransformation = PasswordVisualTransformation())
                    Button({ vm.setup(pin, decoy); pin = ""; decoy = "" }, enabled = pin.length >= 4) { Text("Create") }
                }
            }
            else -> GlassSurface(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pin, { pin = it }, label = { Text("PIN / password") }, visualTransformation = PasswordVisualTransformation(), isError = error.isNotEmpty(), supportingText = { if (error.isNotEmpty()) Text(error) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.attempt(pin); pin = "" }) { Text("Unlock") }
                        OutlinedButton({
                            val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) { vm.auth.unlockWithBiometric(); vm.reload() }
                            })
                            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock private space").setNegativeButtonText("Cancel").build())
                        }) { Text("Biometric") }
                    }
                }
            }
        }
    }
}
