package com.bluemimicry.launcher.engines

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Vertical drag helper: reports the raw pixel delta of each drag step. */
@Composable
fun Modifier.androidScroll(onDelta: (Float) -> Unit): Modifier =
    this.draggable(rememberDraggableState { onDelta(it) }, Orientation.Vertical)
