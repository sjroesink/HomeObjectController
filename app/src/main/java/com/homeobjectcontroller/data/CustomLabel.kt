package com.homeobjectcontroller.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_labels")
data class CustomLabel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mlKitCategory: String,
    val customName: String,
    val featureVector: String, // JSON-serialized FloatArray
    val createdAt: Long = System.currentTimeMillis()
)
