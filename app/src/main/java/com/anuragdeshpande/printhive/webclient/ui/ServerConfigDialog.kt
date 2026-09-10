package com.anuragdeshpande.printhive.webclient.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuragdeshpande.printhive.webclient.data.NetworkHelper
import com.anuragdeshpande.printhive.webclient.data.ServerPreferences

@Composable
fun ServerConfigDialog(
    initialLocalUrl: String,
    initialRemoteUrl: String,
    initialSsid: String,
    initialMode: String,
    onDismiss: () -> Unit,
    onSaveConfig: (localUrl: String, remoteUrl: String, ssid: String, mode: String) -> Unit
) {
    val context = LocalContext.current
    var localUrl by remember { mutableStateOf(initialLocalUrl) }
    var remoteUrl by remember { mutableStateOf(initialRemoteUrl) }
    var ssidText by remember { mutableStateOf(initialSsid) }
    var mode by remember { mutableStateOf(initialMode) }

    var isCheckingLocal by remember { mutableStateOf(false) }
    var localStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingRemote by remember { mutableStateOf(false) }
    var remoteStatus by remember { mutableStateOf<String?>(null) }

    val isWifi = remember { NetworkHelper.isWifiConnected(context) }
    val currentDetectedSsid = remember { NetworkHelper.getCurrentWifiSsid(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = "Server Settings",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Network & Server Settings",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Configure your Home LAN and Remote Tailscale endpoints. PrintHive can automatically switch depending on which Wi-Fi you are connected to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Selection
                Text(
                    text = "Routing Mode",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = mode == ServerPreferences.MODE_AUTO,
                        onClick = { mode = ServerPreferences.MODE_AUTO },
                        label = { Text("Auto", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = mode == ServerPreferences.MODE_LOCAL,
                        onClick = { mode = ServerPreferences.MODE_LOCAL },
                        label = { Text("Home Only", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = mode == ServerPreferences.MODE_REMOTE,
                        onClick = { mode = ServerPreferences.MODE_REMOTE },
                        label = { Text("Remote Only", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Home LAN URL Input
                Text(
                    text = "Home LAN Endpoint (Local Wi-Fi)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = localUrl,
                    onValueChange = {
                        localUrl = it
                        localStatus = null
                    },
                    label = { Text("Home IP / Host") },
                    placeholder = { Text("http://192.168.1.250:8000") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                isCheckingLocal = true
                                NetworkHelper.checkUrlReachable(localUrl) { ok, ms ->
                                    isCheckingLocal = false
                                    localStatus = if (ok) "Online (${ms}ms)" else "Unreachable"
                                }
                            }
                        ) {
                            if (isCheckingLocal) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Test Local Endpoint")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (localStatus != null) {
                    val isOk = localStatus!!.startsWith("Online")
                    Text(
                        text = localStatus!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isOk) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Remote / Tailscale URL Input
                Text(
                    text = "Remote Endpoint (Tailscale / WAN)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = {
                        remoteUrl = it
                        remoteStatus = null
                    },
                    label = { Text("Tailscale IP / Domain") },
                    placeholder = { Text("http://100.65.78.92:8000") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                isCheckingRemote = true
                                NetworkHelper.checkUrlReachable(remoteUrl) { ok, ms ->
                                    isCheckingRemote = false
                                    remoteStatus = if (ok) "Online (${ms}ms)" else "Unreachable"
                                }
                            }
                        ) {
                            if (isCheckingRemote) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Test Remote Endpoint")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (remoteStatus != null) {
                    val isOk = remoteStatus!!.startsWith("Online")
                    Text(
                        text = remoteStatus!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isOk) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Home Wi-Fi SSID
                Text(
                    text = "Home Wi-Fi SSID",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = ssidText,
                    onValueChange = { ssidText = it },
                    label = { Text("Home Wi-Fi Network Name") },
                    placeholder = { Text("e.g. MyHomeWifi (leave blank for any Wi-Fi)") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    },
                    trailingIcon = {
                        if (!currentDetectedSsid.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    ssidText = currentDetectedSsid
                                }
                            ) {
                                Text("Use Current", fontSize = 11.sp)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Live Connection Status Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isWifi) Color(0xFF4CAF50) else Color(0xFF2196F3))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWifi) {
                                    if (!currentDetectedSsid.isNullOrBlank()) "Connected to Wi-Fi: $currentDetectedSsid" else "Connected to Wi-Fi"
                                } else {
                                    "Connected via Mobile / Cellular"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveConfig(localUrl, remoteUrl, ssidText, mode)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
