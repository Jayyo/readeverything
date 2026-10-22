package io.github.jayyo.readeverything.viewer

import androidx.compose.runtime.Composable

@Composable
fun UnsupportedScreen(label: String) {
    CenterMessage("아직 지원하지 않는 형식입니다: $label")
}
