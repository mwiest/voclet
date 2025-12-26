package com.github.mwiest.voclet.ui.wordlist

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.github.mwiest.voclet.R

@Composable
fun CameraPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                onPermissionGranted()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showRationaleDialog = false
                onPermissionDenied()
            },
            title = { Text(stringResource(id = R.string.camera_permission_required)) },
            text = { Text(stringResource(id = R.string.camera_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text(stringResource(id = R.string.grant_permission))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    onPermissionDenied()
                }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}
