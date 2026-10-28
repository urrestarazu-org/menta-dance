package com.menta.android.presentation.mysessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menta.android.R
import com.menta.android.domain.model.MySession
import java.time.ZoneId
import java.util.Locale

private val SESSION_LOCALE: Locale = Locale.forLanguageTag("es-AR")

/**
 * Stateful route. The list is re-fetched on every resume because `checkInOpen` is a
 * snapshot of the query instant. [onShowQr] is `null` while no QR destination exists, so
 * no button is shown.
 */
@Composable
fun MySessionsScreen(
    onShowQr: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: MySessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    MySessionsContent(state = state, onRetry = viewModel::refresh, onShowQr = onShowQr, modifier = modifier)
}

@Composable
fun MySessionsContent(
    state: MySessionsUiState,
    onRetry: () -> Unit,
    onShowQr: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            MySessionsUiState.Loading -> CircularProgressIndicator()
            MySessionsUiState.Empty -> Text(
                text = stringResource(R.string.my_sessions_empty),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            MySessionsUiState.Error -> Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.my_sessions_error), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            }
            is MySessionsUiState.Loaded -> SessionList(state.sessions, onShowQr)
        }
    }
}

@Composable
private fun SessionList(sessions: List<MySession>, onShowQr: ((String) -> Unit)?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = stringResource(R.string.my_sessions_title), style = MaterialTheme.typography.headlineSmall)
        }
        items(sessions, key = MySession::sessionId) { session -> SessionItem(session, onShowQr) }
    }
}

@Composable
private fun SessionItem(session: MySession, onShowQr: ((String) -> Unit)?) {
    val startLabel = remember(session.scheduledAt) {
        formatSessionTime(session.scheduledAt, ZoneId.systemDefault(), SESSION_LOCALE)
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = session.courseName, style = MaterialTheme.typography.titleMedium)
            Text(text = startLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.my_sessions_instructor, session.instructorName),
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                !session.checkInOpen -> Text(
                    text = stringResource(R.string.my_sessions_check_in_closed),
                    style = MaterialTheme.typography.bodySmall,
                )
                onShowQr != null -> Button(onClick = { onShowQr(session.sessionId) }) {
                    Text(stringResource(R.string.my_sessions_show_qr))
                }
            }
        }
    }
}
