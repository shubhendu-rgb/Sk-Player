package com.example.ui.components

object PlayerVolumeKeyHandler {
    var onVolumeKey: ((isUp: Boolean) -> Boolean)? = null
}
