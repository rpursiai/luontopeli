package com.example.luontopeli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "nature_spots")
data class NatureSpot(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val imageLocalPath: String? = null,
    val imageFirebaseUrl: String? = null,
    val plantLabel: String? = null,
    val confidence: Float? = null,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val comment: String = "",
    val category: String = "plant"
)
