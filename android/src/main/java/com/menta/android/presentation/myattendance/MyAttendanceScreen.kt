package com.menta.android.presentation.myattendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
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
import com.menta.android.domain.model.AttendanceEntry
import com.menta.android.presentation.mysessions.formatSessionTime
import java.util.Locale

private val ATTENDANCE_LOCALE: Locale = Locale.forLanguageTag("es-AR")

/** Stateful route. The selected month is re-fetched on every resume and keeps its selection. */
@Composable
fun MyAttendanceScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    MyAttendanceContent(
        state = state,
        onPrevious = viewModel::showPreviousMonth,
        onNext = viewModel::showNextMonth,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
fun MyAttendanceContent(
    state: AttendanceHistoryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MonthSelector(state = state, onPrevious = onPrevious, onNext = onNext)
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val content = state.content) {
                AttendanceContent.Loading -> CircularProgressIndicator()
                AttendanceContent.Empty -> Text(
                    text = stringResource(R.string.my_attendance_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                AttendanceContent.Error -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = stringResource(R.string.my_attendance_error), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }
                is AttendanceContent.Loaded -> AttendanceList(content.entries)
            }
        }
    }
}

/** Visible in every content state so the student can always change month. */
@Composable
private fun MonthSelector(state: AttendanceHistoryUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val label = remember(state.month) { formatMonthLabel(state.month, ATTENDANCE_LOCALE) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious) { Text(stringResource(R.string.my_attendance_previous)) }
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onNext, enabled = state.isNextEnabled) {
            Text(stringResource(R.string.my_attendance_next))
        }
    }
}

@Composable
private fun AttendanceList(entries: List<AttendanceEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = AttendanceEntry::sessionId) { entry -> AttendanceItem(entry) }
    }
}

@Composable
private fun AttendanceItem(entry: AttendanceEntry) {
    val classLabel = remember(entry.scheduledAt) {
        formatSessionTime(entry.scheduledAt, ACADEMY_ZONE, ATTENDANCE_LOCALE)
    }
    val checkInTime = remember(entry.recordedAt) { formatCheckInTime(entry.recordedAt, ACADEMY_ZONE) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = entry.courseName, style = MaterialTheme.typography.titleMedium)
            Text(text = classLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.my_sessions_instructor, entry.instructorName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(entry.origin.labelRes() ?: R.string.my_attendance_check_in, checkInTime),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
