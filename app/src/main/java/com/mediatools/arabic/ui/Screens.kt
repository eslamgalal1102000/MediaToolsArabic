package com.mediatools.arabic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediatools.arabic.JobState
import com.mediatools.arabic.R
import com.mediatools.arabic.ToolsViewModel
import com.mediatools.arabic.core.BitrateCalculator
import com.mediatools.arabic.core.SourceInfo

@Composable
fun AppRoot(vm: ToolsViewModel = viewModel()) {
    val tabs = listOf(
        R.string.tab_download, R.string.tab_convert, R.string.tab_compress,
        R.string.tab_files, R.string.tab_history, R.string.tab_settings,
    )
    var selected by rememberSaveable { mutableIntStateOf(2) }
    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, res ->
                    Tab(
                        selected = selected == i,
                        onClick = { selected = i },
                        text = { Text(stringResource(res)) },
                    )
                }
            }
            when (selected) {
                1 -> ConvertScreen(vm)
                2 -> CompressScreen(vm)
                else -> ComingSoon()
            }
        }
    }
}

@Composable
private fun ComingSoon() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SourceSummary(src: SourceInfo) {
    Text(stringResource(R.string.file_name, src.name))
    Text(stringResource(R.string.file_size, formatSize(src.sizeBytes)))
    if (src.durationMs > 0) {
        Text(stringResource(R.string.file_duration, formatDuration(src.durationMs)))
    } else {
        Text(stringResource(R.string.fail_no_duration), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun JobStatus(state: JobState, onCancel: () -> Unit) {
    when (state) {
        JobState.Idle -> {}
        is JobState.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(state.message)
            }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        is JobState.Done -> Text(state.message, color = MaterialTheme.colorScheme.primary)
        is JobState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun CompressScreen(vm: ToolsViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickCompressSource(uri)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri ->
        if (uri != null) vm.startCompress(uri)
    }
    val src = vm.compressSource
    val targetMb = vm.compressTargetMb.toDoubleOrNull()
    val running = vm.compressState is JobState.Running

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { pick.launch(arrayOf("audio/*", "video/*")) }, enabled = !running) {
            Text(stringResource(R.string.pick_file))
        }
        if (src != null) SourceSummary(src)

        Text(stringResource(R.string.target_size), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20, 25).forEach { mb ->
                FilterChip(
                    selected = vm.compressTargetMb == mb.toString(),
                    onClick = { vm.compressTargetMb = mb.toString() },
                    label = { Text("$mb MB") },
                    enabled = !running,
                )
            }
        }
        OutlinedTextField(
            value = vm.compressTargetMb,
            onValueChange = { v -> vm.compressTargetMb = v.filter { it.isDigit() || it == '.' } },
            label = { Text(stringResource(R.string.custom_mb)) },
            singleLine = true,
            enabled = !running,
        )
        Text(stringResource(R.string.mb_note), style = MaterialTheme.typography.bodySmall)

        if (src != null && src.durationMs > 0 && targetMb != null && targetMb > 0) {
            val targetBytes = (targetMb * BitrateCalculator.BYTES_PER_MB).toLong()
            val kbps = BitrateCalculator.suggestedKbps(targetBytes, src.durationMs)
            if (BitrateCalculator.isFeasible(kbps)) {
                val used = kbps.coerceAtMost(BitrateCalculator.MAX_KBPS)
                Text(stringResource(R.string.suggested_bitrate, used))
                Text(stringResource(R.string.estimated_size, formatSize(BitrateCalculator.estimateBytes(used, src.durationMs))))
            } else {
                Text(
                    stringResource(R.string.fail_impossible, BitrateCalculator.MIN_KBPS),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(stringResource(R.string.output_format_note), style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                val base = src!!.name.substringBeforeLast('.')
                save.launch("${base}_compressed.m4a")
            },
            enabled = src != null && src.durationMs > 0 && targetMb != null && targetMb > 0 && !running,
        ) { Text(stringResource(R.string.start)) }

        JobStatus(vm.compressState, vm::cancel)
    }
}

@Composable
fun ConvertScreen(vm: ToolsViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickConvertSource(uri)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri ->
        if (uri != null) vm.startConvert(uri)
    }
    val src = vm.convertSource
    val running = vm.convertState is JobState.Running

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { pick.launch(arrayOf("video/*", "audio/*")) }, enabled = !running) {
            Text(stringResource(R.string.pick_file))
        }
        if (src != null) SourceSummary(src)

        Text(stringResource(R.string.quality_title), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(96, 128, 192, 256, 320).forEach { k ->
                FilterChip(
                    selected = vm.convertKbps == k,
                    onClick = { vm.convertKbps = k },
                    label = { Text("$k kbps") },
                    enabled = !running,
                )
            }
        }
        Text(stringResource(R.string.quality_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.output_format_note), style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                val base = src!!.name.substringBeforeLast('.')
                save.launch("$base.m4a")
            },
            enabled = src != null && !running,
        ) { Text(stringResource(R.string.start)) }

        JobStatus(vm.convertState, vm::cancel)
    }
}
