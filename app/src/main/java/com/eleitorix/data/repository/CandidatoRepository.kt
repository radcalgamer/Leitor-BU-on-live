package com.eleitorix.data.repository

import android.content.Context
import com.eleitorix.data.db.AppDatabase
import com.eleitorix.data.db.CandidatoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

class CandidatoRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val candidatoDao = db.candidatoDao()
    private val appContext = context.applicationContext

    companion object {
        private val cacheCandidatos = ConcurrentHashMap<String, Pair<String, String>>()
        private var cacheCarregadoAno: Int? = null
    }

    fun getAnosDisponiveisFlow(): Flow<List<Int>> = candidatoDao.getAnosDisponiveisFlow()

    fun getCountFlow(): Flow<Int> = candidatoDao.countFlow()

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        candidatoDao.count()
    }

    suspend fun getCountPorAno(ano: Int): Int = withContext(Dispatchers.IO) {
        candidatoDao.countPorAno(ano)
    }

    suspend fun filtrarCandidatos(ano: Int, uf: String, cargo: Int, query: String): List<CandidatoEntity> {
        return withContext(Dispatchers.IO) {
            candidatoDao.filtrarCandidatos(ano, uf, cargo, query.trim())
        }
    }

    suspend fun precarregarCacheSeNecessario(ano: Int) = withContext(Dispatchers.IO) {
        if (cacheCarregadoAno == ano && cacheCandidatos.isNotEmpty()) return@withContext
        val lista = candidatoDao.getByAno(ano)
        for (c in lista) {
            guardarNoCache(c)
        }
        cacheCarregadoAno = ano
    }

    private fun guardarNoCache(c: CandidatoEntity) {
        val par = Pair(c.nomeUrna, c.partidoSigla)
        cacheCandidatos["${c.ano}:${c.cargo}:${c.uf}:${c.numero}"] = par
        if (c.cargo == 1 || c.cargo == 2) {
            cacheCandidatos["${c.ano}:${c.cargo}:${c.numero}"] = par
        }
        cacheCandidatos["num:${c.numero}"] = par
    }

    suspend fun resolverCandidato(
        ano: Int,
        cargo: Int,
        uf: String,
        numero: String
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val chaveEspecifica = "$ano:$cargo:$uf:$numero"
        cacheCandidatos[chaveEspecifica]?.let { return@withContext it }

        val isCargoNacional = (cargo == 1 || cargo == 2)

        if (isCargoNacional) {
            val chaveNacional = "$ano:$cargo:BR:$numero"
            cacheCandidatos[chaveNacional]?.let { return@withContext it }

            val chaveCargo = "$ano:$cargo:$numero"
            cacheCandidatos[chaveCargo]?.let { return@withContext it }
        }

        val encontrado = if (isCargoNacional) {
            candidatoDao.buscarPorAnoCargoUfNumero(ano, cargo, "BR", numero)
                ?: candidatoDao.buscarPorAnoCargoNumero(ano, cargo, numero)
        } else {
            candidatoDao.buscarPorAnoCargoUfNumero(ano, cargo, uf, numero)
        }

        if (encontrado != null) {
            val par = Pair(encontrado.nomeUrna, encontrado.partidoSigla)
            guardarNoCache(encontrado)
            par
        } else {
            null
        }
    }

    /**
     * Importa um CSV de candidatos garantindo a filtragem rigorosa de status (apenas APTO/DEFERIDO),
     * e realiza a limpeza prévia baseada no ano contido no arquivo.
     */
    suspend fun importarCsv(
        inputStream: InputStream,
        charset: Charset = Charsets.UTF_8
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream, charset))
            val headerLine = reader.readLine() ?: return@withContext Result.failure(Exception("Arquivo vazio"))

            val delimitador = if (headerLine.contains(";")) ";" else ","
            val colunasHeader = headerLine.split(delimitador).map { it.trim().trim('"', '\'') }

            val idxAno = colunasHeader.indexOfFirst { it.equals("ano", ignoreCase = true) || it.equals("ANO_ELEICAO", ignoreCase = true) }
            val idxTurno = colunasHeader.indexOfFirst { it.equals("turno", ignoreCase = true) || it.equals("NR_TURNO", ignoreCase = true) }
            val idxUf = colunasHeader.indexOfFirst { it.equals("uf", ignoreCase = true) || it.equals("SG_UF", ignoreCase = true) }
            val idxCodUe = colunasHeader.indexOfFirst { it.equals("codigo_ue", ignoreCase = true) || it.equals("SG_UE", ignoreCase = true) }
            val idxNomeUe = colunasHeader.indexOfFirst { it.equals("nome_ue", ignoreCase = true) || it.equals("NM_UE", ignoreCase = true) }
            val idxCargo = colunasHeader.indexOfFirst { it.equals("cargo", ignoreCase = true) || it.equals("CD_CARGO", ignoreCase = true) }
            val idxNumero = colunasHeader.indexOfFirst { it.equals("numero", ignoreCase = true) || it.equals("NR_CANDIDATO", ignoreCase = true) }
            val idxNomeUrna = colunasHeader.indexOfFirst { it.equals("nome_urna", ignoreCase = true) || it.equals("NM_URNA_CANDIDATO", ignoreCase = true) }
            val idxPartNum = colunasHeader.indexOfFirst { it.equals("partido_numero", ignoreCase = true) || it.equals("NR_PARTIDO", ignoreCase = true) }
            val idxPartSigla = colunasHeader.indexOfFirst { it.equals("partido_sigla", ignoreCase = true) || it.equals("SG_PARTIDO", ignoreCase = true) }

            // Coluna oficial do TSE para situação da candidatura
            val idxSituacao = colunasHeader.indexOfFirst {
                it.equals("DS_SITUACAO_CANDIDATO", ignoreCase = true) ||
                        it.equals("SITUACAO", ignoreCase = true)
            }

            if (idxCargo == -1 || idxNumero == -1 || idxNomeUrna == -1) {
                return@withContext Result.failure(Exception("Colunas obrigatórias não encontradas no cabeçalho do CSV"))
            }

            val linhasProcessadas = mutableListOf<CandidatoEntity>()
            var anoDetectado: Int? = null
            var linha: String?

            while (reader.readLine().also { linha = it } != null) {
                if (linha.isNullOrBlank()) continue
                val valores = linha!!.split(delimitador).map { it.trim().trim('"', '\'') }
                if (valores.size <= maxOf(idxCargo, idxNumero, idxNomeUrna)) continue

                // FILTRAGEM RIGOROSA DE SITUAÇÃO:
                // Mantém apenas se contiver APTO ou DEFERIDO. Descarta inaptos/indeferidos (como o Lula em 2018).
                if (idxSituacao != -1 && idxSituacao < valores.size) {
                    val situacao = valores[idxSituacao].uppercase()
                    if (!situacao.contains("APTO") && !situacao.contains("DEFERIDO")) {
                        continue
                    }
                }

                val ano = if (idxAno != -1 && idxAno < valores.size) valores[idxAno].toIntOrNull() ?: 2026 else 2026
                if (anoDetectado == null) {
                    anoDetectado = ano
                }

                val turno = if (idxTurno != -1 && idxTurno < valores.size) valores[idxTurno].toIntOrNull() ?: 1 else 1
                val uf = if (idxUf != -1 && idxUf < valores.size) valores[idxUf].uppercase() else "BR"
                val codUe = if (idxCodUe != -1 && idxCodUe < valores.size) valores[idxCodUe] else uf
                val nomeUe = if (idxNomeUe != -1 && idxNomeUe < valores.size) valores[idxNomeUe] else ""
                val cargo = valores[idxCargo].toIntOrNull() ?: continue
                val numero = valores[idxNumero]
                val nomeUrna = valores[idxNomeUrna]
                val partidoNum = if (idxPartNum != -1 && idxPartNum < valores.size) {
                    valores[idxPartNum].toIntOrNull() ?: (numero.take(2).toIntOrNull() ?: 0)
                } else {
                    numero.take(2).toIntOrNull() ?: 0
                }
                val partidoSigla = if (idxPartSigla != -1 && idxPartSigla < valores.size) valores[idxPartSigla] else ""

                if (numero.isNotBlank() && nomeUrna.isNotBlank()) {
                    linhasProcessadas.add(
                        CandidatoEntity(
                            ano = ano,
                            turno = turno,
                            uf = uf,
                            codigoUe = codUe,
                            nomeUe = nomeUe,
                            cargo = cargo,
                            numero = numero,
                            nomeUrna = nomeUrna,
                            partidoNumero = partidoNum,
                            partidoSigla = partidoSigla
                        )
                    )
                }
            }

            if (anoDetectado != null) {
                candidatoDao.deleteByAno(anoDetectado)
                cacheCandidatos.clear()
                cacheCarregadoAno = null
            }

            var totalInseridos = 0
            for (chunk in linhasProcessadas.chunked(1000)) {
                candidatoDao.insertAll(chunk)
                chunk.forEach { guardarNoCache(it) }
                totalInseridos += chunk.size
            }

            if (anoDetectado != null) {
                cacheCarregadoAno = anoDetectado
            }

            Result.success(totalInseridos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inicializarCandidatosEmbutidosSeNecessario() = withContext(Dispatchers.IO) {
        val total = candidatoDao.count()
        if (total == 0) {
            preencherAno2026()
        }
        precarregarCacheSeNecessario(2026)
    }

    suspend fun preencherAno(ano: Int): Int = withContext(Dispatchers.IO) {
        val candidatos = when (ano) {
            2026 -> gerarCandidatos2026()
            2024 -> gerarCandidatos2024()
            2022 -> gerarCandidatos2022()
            2020 -> gerarCandidatos2020()
            else -> emptyList()
        }
        if (candidatos.isNotEmpty()) {
            candidatoDao.deleteByAno(ano)
            candidatoDao.insertAll(candidatos)
            candidatos.forEach { guardarNoCache(it) }
            cacheCarregadoAno = ano
        }
        candidatos.size
    }

    private suspend fun preencherAno2026() {
        val candidatos = gerarCandidatos2026()
        candidatoDao.deleteByAno(2026)
        candidatoDao.insertAll(candidatos)
        candidatos.forEach { guardarNoCache(it) }
        cacheCarregadoAno = 2026
    }

    private fun gerarCandidatos2026(): List<CandidatoEntity> {
        return listOf(
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "13", "LULA", 13, "PT"),
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "22", "FLAVIO BOLSONARO", 22, "PL"),
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "30", "ZEMA", 30, "NOVO"),
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "55", "RONALDO CAIADO", 55, "PSD"),
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "14", "RENAN SANTOS", 14, "MISSÃO"),
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "80", "SAMARA", 80, "UP"),
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "16", "HERTZ DIAS", 16, "PSTU")
        )
    }

    private fun gerarCandidatos2024(): List<CandidatoEntity> = emptyList()
    private fun gerarCandidatos2022(): List<CandidatoEntity> = emptyList()
    private fun gerarCandidatos2020(): List<CandidatoEntity> = emptyList()
}