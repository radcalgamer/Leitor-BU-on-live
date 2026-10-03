package com.eleitorix.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "boletins_urna",
    indices = [
        Index(value = ["ano", "turno", "uf", "zona", "secao"], unique = true)
    ]
)
data class BuEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ano: Int,
    val pleito: String,
    val turno: Int,
    val uf: String,
    val municipio: String,
    val municipioNome: String?,
    val zona: Int,
    val secao: Int,
    val aptos: Int,
    val comparecimento: Int,
    val faltas: Int,
    val dataHoraAbertura: String,
    val dataHoraFechamento: String,
    val rawJson: String,
    val hashSha512: String?,
    val assinatura: String?,
    val assinaturaValida: Boolean?,
    val criadoEm: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "candidatos",
    indices = [
        Index(value = ["ano", "turno", "uf", "cargo", "numero"]),
        Index(value = ["ano", "cargo"]),
        Index(value = ["nomeUrna"])
    ]
)
data class CandidatoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ano: Int,
    val turno: Int,
    val uf: String,
    val codigoUe: String,
    val nomeUe: String,
    val cargo: Int,
    val numero: String,
    val nomeUrna: String,
    val partidoNumero: Int,
    val partidoSigla: String
)
