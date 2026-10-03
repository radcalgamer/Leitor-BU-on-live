package com.eleitorix.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuDao {

    @Query("SELECT * FROM boletins_urna ORDER BY criadoEm DESC")
    fun getAllFlow(): Flow<List<BuEntity>>

    @Query("SELECT * FROM boletins_urna ORDER BY criadoEm DESC")
    suspend fun getAll(): List<BuEntity>

    @Query("SELECT * FROM boletins_urna WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BuEntity?

    @Query("SELECT * FROM boletins_urna WHERE ano = :ano AND turno = :turno ORDER BY criadoEm DESC")
    fun getByEleicaoFlow(ano: Int, turno: Int): Flow<List<BuEntity>>

    @Query("SELECT COUNT(*) FROM boletins_urna")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM boletins_urna")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BuEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BuEntity>): List<Long>

    @Delete
    suspend fun delete(entity: BuEntity)

    @Query("DELETE FROM boletins_urna WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM boletins_urna")
    suspend fun deleteAll()
}

@Dao
interface CandidatoDao {

    @Query("SELECT * FROM candidatos WHERE ano = :ano AND cargo = :cargo AND numero = :numero LIMIT 1")
    suspend fun buscarPorAnoCargoNumero(ano: Int, cargo: Int, numero: String): CandidatoEntity?

    @Query("SELECT * FROM candidatos WHERE ano = :ano AND cargo = :cargo AND (uf = :uf OR uf = 'BR' OR :uf = 'BR') AND numero = :numero LIMIT 1")
    suspend fun buscarPorAnoCargoUfNumero(ano: Int, cargo: Int, uf: String, numero: String): CandidatoEntity?

    @Query("SELECT * FROM candidatos WHERE numero = :numero LIMIT 1")
    suspend fun buscarPorNumero(numero: String): CandidatoEntity?

    @Query("SELECT * FROM candidatos WHERE ano = :ano ORDER BY nomeUrna ASC")
    fun getByAnoFlow(ano: Int): Flow<List<CandidatoEntity>>

    @Query("SELECT * FROM candidatos WHERE ano = :ano")
    suspend fun getByAno(ano: Int): List<CandidatoEntity>

    @Query("SELECT * FROM candidatos")
    suspend fun getAll(): List<CandidatoEntity>

    @Query("SELECT DISTINCT ano FROM candidatos ORDER BY ano DESC")
    fun getAnosDisponiveisFlow(): Flow<List<Int>>

    @Query("SELECT DISTINCT ano FROM candidatos ORDER BY ano DESC")
    suspend fun getAnosDisponiveis(): List<Int>

    @Query("SELECT COUNT(*) FROM candidatos")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM candidatos")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM candidatos WHERE ano = :ano")
    suspend fun countPorAno(ano: Int): Int

    @Query("SELECT * FROM candidatos WHERE ano = :ano AND (:uf = '' OR uf = :uf) AND (:cargo = 0 OR cargo = :cargo) AND (nomeUrna LIKE '%' || :query || '%' OR numero LIKE '%' || :query || '%' OR partidoSigla LIKE '%' || :query || '%') ORDER BY nomeUrna ASC LIMIT 200")
    suspend fun filtrarCandidatos(ano: Int, uf: String, cargo: Int, query: String): List<CandidatoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidatos: List<CandidatoEntity>)

    @Query("DELETE FROM candidatos WHERE ano = :ano")
    suspend fun deleteByAno(ano: Int)

    @Query("DELETE FROM candidatos")
    suspend fun deleteAll()
}
