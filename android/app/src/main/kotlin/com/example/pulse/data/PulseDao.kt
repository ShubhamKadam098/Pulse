package com.example.pulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PulseDao {
    @Insert
    fun insertSession(session: SessionEntity): Long
    @Update
    fun updateSession(session: SessionEntity)

    @Insert
    fun insertAcknowledgement(ack: AcknowledgementEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM acknowledgements WHERE timestamp >= :start AND timestamp <= :end")
    fun getAcknowledgementsInRange(start: Long, end: Long): List<AcknowledgementEntity>

    @Query("SELECT COUNT(*) FROM acknowledgements")
    fun getTotalAcknowledgements(): Int

    @Query("SELECT * FROM acknowledgements")
    fun getAllAcknowledgements(): List<AcknowledgementEntity>

    @Query("SELECT timestamp FROM acknowledgements ORDER BY timestamp ASC")
    fun getAllAcknowledgementTimestamps(): List<Long>
}
