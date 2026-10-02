package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.CandidatoEntity
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

    // Cache em memória para resolução instantânea sem consultas repetitivas a disco
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

    /**
     * Pré-carrega o cache em memória para um ano específico em uma única consulta rápida.
     */
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
        cacheCandidatos["${c.ano}:${c.cargo}:${c.numero}"] = par
        cacheCandidatos["num:${c.numero}"] = par
    }

    /**
     * Resolve o nome de urna e sigla do partido para um número de candidato.
     * Utiliza cache em memória para resposta instantânea de 0ms sem gargalo de I/O.
     */
    suspend fun resolverCandidato(
        ano: Int,
        cargo: Int,
        uf: String,
        numero: String
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val chaveEspecifica = "$ano:$cargo:$uf:$numero"
        cacheCandidatos[chaveEspecifica]?.let { return@withContext it }

        val chaveNacional = "$ano:$cargo:BR:$numero"
        cacheCandidatos[chaveNacional]?.let { return@withContext it }

        val chaveCargo = "$ano:$cargo:$numero"
        cacheCandidatos[chaveCargo]?.let { return@withContext it }

        val chaveNum = "num:$numero"
        cacheCandidatos[chaveNum]?.let { return@withContext it }

        // Se ainda não estava no cache, busca no banco e armazena
        val encontrado = candidatoDao.buscarPorAnoCargoUfNumero(ano, cargo, uf, numero)
            ?: candidatoDao.buscarPorAnoCargoNumero(ano, cargo, numero)
            ?: candidatoDao.buscarPorNumero(numero)

        if (encontrado != null) {
            val par = Pair(encontrado.nomeUrna, encontrado.partidoSigla)
            guardarNoCache(encontrado)
            par
        } else {
            null
        }
    }

    /**
     * Importa um CSV de candidatos (suporta formato oficial TSE com ';' e enxuto com ',')
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

            if (idxCargo == -1 || idxNumero == -1 || idxNomeUrna == -1) {
                return@withContext Result.failure(Exception("Colunas obrigatórias não encontradas no cabeçalho do CSV"))
            }

            val batch = mutableListOf<CandidatoEntity>()
            var totalInseridos = 0
            var linha: String?

            while (reader.readLine().also { linha = it } != null) {
                if (linha.isNullOrBlank()) continue
                val valores = linha!!.split(delimitador).map { it.trim().trim('"', '\'') }
                if (valores.size <= maxOf(idxCargo, idxNumero, idxNomeUrna)) continue

                val ano = if (idxAno != -1 && idxAno < valores.size) valores[idxAno].toIntOrNull() ?: 2026 else 2026
                val turno = if (idxTurno != -1 && idxTurno < valores.size) valores[idxTurno].toIntOrNull() ?: 1 else 1
                val uf = if (idxUf != -1 && idxUf < valores.size) valores[idxUf].uppercase() else "BR"
                val codUe = if (idxCodUe != -1 && idxCodUe < valores.size) valores[idxCodUe] else uf
                val nomeUe = if (idxNomeUe != -1 && idxNomeUe < valores.size) valores[idxNomeUe] else ""
                val cargo = valores[idxCargo].toIntOrNull() ?: continue
                val numero = valores[idxNumero]
                val nomeUrna = valores[idxNomeUrna]
                val partidoNum = if (idxPartNum != -1 && idxPartNum < valores.size) valores[idxPartNum].toIntOrNull() ?: (numero.take(2).toIntOrNull() ?: 0) else (numero.take(2).toIntOrNull() ?: 0)
                val partidoSigla = if (idxPartSigla != -1 && idxPartSigla < valores.size) valores[idxPartSigla] else ""

                if (numero.isNotBlank() && nomeUrna.isNotBlank()) {
                    val entidade = CandidatoEntity(
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
                    batch.add(entidade)
                    guardarNoCache(entidade)
                }

                if (batch.size >= 500) {
                    candidatoDao.insertAll(batch)
                    totalInseridos += batch.size
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                candidatoDao.insertAll(batch)
                totalInseridos += batch.size
                batch.clear()
            }

            Result.success(totalInseridos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicializa os dados embutidos de candidatos de 2026 caso ainda não existam
     */
    suspend fun inicializarCandidatosEmbutidosSeNecessario() = withContext(Dispatchers.IO) {
        val total = candidatoDao.count()
        if (total == 0) {
            try {
                appContext.assets.open("candidatos/candidatos_2026.csv").use { stream ->
                    importarCsv(stream, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                preencherAno2026()
            }
        }
        precarregarCacheSeNecessario(2026)
    }

    /**
     * Preenche automaticamente o banco com dados eleitorais dos outros anos (2026, 2024, 2022, 2020)
     */
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
            CandidatoEntity(0, 2026, 1, "BR", "BR", "BRASIL", 1, "16", "HERTZ DIAS", 16, "PSTU"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "55", "EDUARDO PAES", 55, "PSD"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "22", "DOUGLAS RUAS", 22, "PL"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "10", "GAROTINHO", 10, "REPUBLICANOS"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "30", "ANDRÉ MARINHO", 30, "NOVO"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "50", "WILLIAM SIRI", 50, "PSOL"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 5, "555", "PEDRO PAULO", 55, "PSD"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 5, "222", "CARLOS PORTINHO", 22, "PL"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 5, "131", "BENEDITA DA SILVA", 13, "PT"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 5, "100", "MARCELO CRIVELLA", 10, "REPUBLICANOS"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 6, "2212", "GENERAL PAZUELLO", 22, "PL"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 6, "1300", "LINDBERGH", 13, "PT"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 6, "5000", "TARCÍSIO MOTTA", 50, "PSOL"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 6, "5588", "DANIEL SORANZ", 55, "PSD"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 6, "1177", "DR. LUIZINHO", 11, "PP"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 6, "3030", "LUIZ LIMA", 30, "NOVO"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 7, "44444", "MÁRCIO CANELLA", 44, "UNIÃO"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 7, "22222", "GUILHERME DELAROLI", 22, "PL"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 7, "50007", "RENATA SOUZA", 50, "PSOL"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 7, "55555", "GUILHERME SCHLEDER", 55, "PSD"),
            CandidatoEntity(0, 2026, 1, "RJ", "RJ", "RIO DE JANEIRO", 7, "10123", "TIA JU", 10, "REPUBLICANOS")
        )
    }

    private fun gerarCandidatos2024(): List<CandidatoEntity> {
        return listOf(
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 11, "15", "RICARDO NUNES", 15, "MDB"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 11, "50", "GUILHERME BOULOS", 50, "PSOL"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 11, "28", "PABLO MARÇAL", 28, "PRTB"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 11, "40", "TABATA AMARAL", 40, "PSB"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 11, "45", "JOSÉ LUIZ DATENA", 45, "PSDB"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 11, "30", "MARINA HELENA", 30, "NOVO"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 13, "22111", "LUCAS PAVANATO", 22, "PL"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 13, "20026", "ANA CAROLINA OLIVEIRA", 20, "PODE"),
            CandidatoEntity(0, 2024, 1, "SP", "71072", "SÃO PAULO", 13, "13000", "DR. MURILLO LIMA", 13, "PT"),
            CandidatoEntity(0, 2024, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "55", "EDUARDO PAES", 55, "PSD"),
            CandidatoEntity(0, 2024, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "22", "ALEXANDRE RAMAGEM", 22, "PL"),
            CandidatoEntity(0, 2024, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "50", "TARCÍSIO MOTTA", 50, "PSOL"),
            CandidatoEntity(0, 2024, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "20", "CAROL SPONZA", 30, "NOVO"),
            CandidatoEntity(0, 2024, 1, "RJ", "60011", "RIO DE JANEIRO", 13, "22222", "CARLOS BOLSONARO", 22, "PL"),
            CandidatoEntity(0, 2024, 1, "RJ", "60011", "RIO DE JANEIRO", 13, "55555", "MARCIO RIBEIRO", 55, "PSD"),
            CandidatoEntity(0, 2024, 1, "RJ", "60011", "RIO DE JANEIRO", 13, "50000", "RICK AZEVEDO", 50, "PSOL"),
            CandidatoEntity(0, 2024, 1, "MG", "41238", "BELO HORIZONTE", 11, "55", "FUAD NOMAN", 55, "PSD"),
            CandidatoEntity(0, 2024, 1, "MG", "41238", "BELO HORIZONTE", 11, "22", "BRUNO ENGLER", 22, "PL"),
            CandidatoEntity(0, 2024, 1, "MG", "41238", "BELO HORIZONTE", 11, "10", "MAURO TRAMONTE", 10, "REPUBLICANOS"),
            CandidatoEntity(0, 2024, 1, "MG", "41238", "BELO HORIZONTE", 11, "20", "GABRIEL AZEVEDO", 15, "MDB"),
            CandidatoEntity(0, 2024, 1, "MG", "41238", "BELO HORIZONTE", 11, "13", "ROGÉRIO CORREIA", 13, "PT"),
            CandidatoEntity(0, 2024, 1, "MG", "41238", "BELO HORIZONTE", 11, "12", "DUDA SALABERT", 12, "PDT")
        )
    }

    private fun gerarCandidatos2022(): List<CandidatoEntity> {
        return listOf(
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "13", "LULA", 13, "PT"),
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "22", "JAIR BOLSONARO", 22, "PL"),
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "15", "SIMONE TEBET", 15, "MDB"),
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "12", "CIRO GOMES", 12, "PDT"),
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "44", "SORAYA THRONICKE", 44, "UNIÃO"),
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "30", "FELIPE D'AVILA", 30, "NOVO"),
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "14", "PADRE KELMON", 14, "PTB"),
            CandidatoEntity(0, 2022, 1, "BR", "BR", "BRASIL", 1, "80", "LEO PÉRICLES", 80, "UP"),
            CandidatoEntity(0, 2022, 1, "SP", "SP", "SÃO PAULO", 3, "10", "TARCÍSIO DE FREITAS", 10, "REPUBLICANOS"),
            CandidatoEntity(0, 2022, 1, "SP", "SP", "SÃO PAULO", 3, "13", "FERNANDO HADDAD", 13, "PT"),
            CandidatoEntity(0, 2022, 1, "SP", "SP", "SÃO PAULO", 3, "45", "RODRIGO GARCIA", 45, "PSDB"),
            CandidatoEntity(0, 2022, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "22", "CLÁUDIO CASTRO", 22, "PL"),
            CandidatoEntity(0, 2022, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "40", "MARCELO FREIXO", 40, "PSB"),
            CandidatoEntity(0, 2022, 1, "RJ", "RJ", "RIO DE JANEIRO", 3, "12", "RODRIGO NEVES", 12, "PDT"),
            CandidatoEntity(0, 2022, 1, "MG", "MG", "MINAS GERAIS", 3, "30", "ROMEU ZEMA", 30, "NOVO"),
            CandidatoEntity(0, 2022, 1, "MG", "MG", "MINAS GERAIS", 3, "55", "ALEXANDRE KALIL", 55, "PSD"),
            CandidatoEntity(0, 2022, 1, "BA", "BA", "BAHIA", 3, "13", "JERÔNIMO RODRIGUES", 13, "PT"),
            CandidatoEntity(0, 2022, 1, "BA", "BA", "BAHIA", 3, "44", "ACM NETO", 44, "UNIÃO"),
            CandidatoEntity(0, 2022, 1, "RS", "RS", "RIO GRANDE DO SUL", 3, "45", "EDUARDO LEITE", 45, "PSDB"),
            CandidatoEntity(0, 2022, 1, "RS", "RS", "RIO GRANDE DO SUL", 3, "22", "ONYX LORENZONI", 22, "PL")
        )
    }

    private fun gerarCandidatos2020(): List<CandidatoEntity> {
        return listOf(
            CandidatoEntity(0, 2020, 1, "SP", "71072", "SÃO PAULO", 11, "45", "BRUNO COVAS", 45, "PSDB"),
            CandidatoEntity(0, 2020, 1, "SP", "71072", "SÃO PAULO", 11, "50", "GUILHERME BOULOS", 50, "PSOL"),
            CandidatoEntity(0, 2020, 1, "SP", "71072", "SÃO PAULO", 11, "40", "MÁRCIO FRANÇA", 40, "PSB"),
            CandidatoEntity(0, 2020, 1, "SP", "71072", "SÃO PAULO", 11, "10", "CELSO RUSSOMANNO", 10, "REPUBLICANOS"),
            CandidatoEntity(0, 2020, 1, "SP", "71072", "SÃO PAULO", 11, "13", "JILMAR TATTO", 13, "PT"),
            CandidatoEntity(0, 2020, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "25", "EDUARDO PAES", 25, "DEM"),
            CandidatoEntity(0, 2020, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "10", "MARCELO CRIVELLA", 10, "REPUBLICANOS"),
            CandidatoEntity(0, 2020, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "12", "MARTHA ROCHA", 12, "PDT"),
            CandidatoEntity(0, 2020, 1, "RJ", "60011", "RIO DE JANEIRO", 11, "13", "BENEDITA DA SILVA", 13, "PT")
        )
    }
}
