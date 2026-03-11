package com.kadamshubham098.pulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "acknowledgements")
data class AcknowledgementEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val durationSeconds: Int = 0,
    val wasFocusing: Boolean = true
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val startTime: Long,
    var endTime: Long? = null,
    var acknowledgementCount: Int = 0
)
