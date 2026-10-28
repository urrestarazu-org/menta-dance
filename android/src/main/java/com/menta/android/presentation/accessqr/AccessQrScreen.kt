package com.menta.android.presentation.accessqr

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menta.android.R
import com.menta.android.domain.model.AccessDenial

/**
 * Stateful route for the access QR. The renewal loop runs only while the screen is STARTED, and the
 * window effects cover the whole destination so no state of it can be captured in a screenshot.
 */
@Composable
fun AccessQrRoute(sessionId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<AccessQrViewModel, AccessQrViewModel.Factory>(
        creationCallback = { factory -> factory.create(sessionId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    SecureScreenEffect()
    if (state is AccessQrUiState.Showing) BrightnessBoostEffect()

    AccessQrContent(state = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessQrContent(state: AccessQrUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.access_qr_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.access_qr_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            when (state) {
                AccessQrUiState.Loading -> Progress(R.string.access_qr_loading)
                AccessQrUiState.Offline -> Progress(R.string.access_qr_offline)
                is AccessQrUiState.Showing -> ShowingQr(state)
                is AccessQrUiState.Denied -> Message(state.reason.messageRes)
            }
        }
    }
}

@Composable
private fun ShowingQr(state: AccessQrUiState.Showing) {
    // Encoded per credential value; the matrix is rebuilt only when a renewal brings a new one.
    val matrix = remember(state.credential.value) { QrEncoder.encode(state.credential.value) }
    if (matrix == null) {
        Message(R.string.access_qr_error)
    } else {
        QrCode(matrix = matrix, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Message(R.string.access_qr_hint)
    }
}

@Composable
private fun Progress(@StringRes message: Int) {
    CircularProgressIndicator()
    Message(message)
}

@Composable
private fun Message(@StringRes message: Int) {
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

private val AccessDenial.messageRes: Int
    @StringRes get() = when (this) {
        AccessDenial.OUTSIDE_CHECK_IN_WINDOW -> R.string.access_qr_denied_outside_window
        AccessDenial.SESSION_CANCELLED -> R.string.access_qr_denied_cancelled
        AccessDenial.CAPACITY_ASSIGNMENT_REQUIRED -> R.string.access_qr_denied_no_capacity
        AccessDenial.SESSION_NOT_FOUND -> R.string.access_qr_denied_not_found
        AccessDenial.OTHER -> R.string.access_qr_error
    }
