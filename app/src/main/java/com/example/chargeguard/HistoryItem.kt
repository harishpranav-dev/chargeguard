package com.example.chargeguard

data class HistoryItem(
    val title: String,
    val batteryPercent: Int,
    val timestamp: String,
    val isAlert: Boolean
)