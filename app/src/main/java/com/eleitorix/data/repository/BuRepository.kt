package com.eleitorix.data.repository

import android.content.Context
import com.eleitorix.data.db.AppDatabase
import com.eleitorix.data.db.BuEntity
import com.eleitorix.model.BoletimUrna
import com.eleitorix.model.CargoNomes
import com.eleitorix.model.CargoVotacao
import com.eleitorix.model.EleicaoVotacao
import com.eleitorix.model.SegundoTurnoProjecao
import com.eleitorix.model.VotoCandidato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BuRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val buDao = db.buDao()
    private val candidatoRepository = CandidatoRepository(context)

    fun getBoletinsFlow(): Flow<List<BoletimUrna>> {
        return buDao.getAllFlow().map { entities ->
            entities.map { entityParaBoletim(it) }
        }
    }

    suspend fun getBoletimPorId(id: Long): BoletimUrna? = withContext(Dispatchers.IO) {
        val entity = buDao.getById(id) ?: return@withContext null
        val bu = entityParaBoletim(entity)
        resolverNomesCandidatosNoBu(bu)
    }

    suspend fun salvarBoletim(boletim: BoletimUrna): Long = withContext(Dispatchers.IO) {
        // Primeiro resolve os nomes dos candidatos para já gravar com dados enriquecidos se possível
        val buEnriquecido = resolverNomesCandidatosNoBu(boletim)
        val entity = boletimParaEntity(buEnriquecido)
        buDao.insert(entity)
    }

    suspend fun excluirBoletim(id: Long) = withContext(Dispatchers.IO) {
        buDao.deleteById(id)
    }

    suspend fun limparTodos() = withContext(Dispatchers.IO) {
        buDao.deleteAll()
    }

    /**
     * Resolve os nomes e siglas de partidos de todos os candidatos em um BU
     * usando a base de dados de candidatos.
     */
    suspend fun resolverNomesCandidatosNoBu(bu: BoletimUrna): BoletimUrna = withContext(Dispatchers.IO) {
        val eleicoesEnriquecidas = bu.eleicoes.map { eleicao ->
            val cargosEnriquecidos = eleicao.cargos.map { cargo ->
                val votosEnriquecidos = cargo.votosCandidatos.map { voto ->
                    val resolvido = candidatoRepository.resolverCandidato(
                        ano = bu.anoEleicao,
                        cargo = cargo.codigoCargo,
                        uf = bu.uf,
                        numero = voto.numero
                    )
                    if (resolvido != null) {
                        voto.copy(
                            nomeUrna = resolvido.first,
                            partidoSigla = resolvido.second,
                            partidoNumero = voto.numero.take(2).toIntOrNull()
                        )
                    } else {
                        // Fallback mais amigável do que "candidato XXXX"
                        val partidoNum = voto.numero.take(2).toIntOrNull()
                        val siglaFallback = if (partidoNum != null) "Partido $partidoNum" else null
                        voto.copy(
                            nomeUrna = voto.nomeUrna ?: "Candidato ${voto.numero}",
                            partidoSigla = voto.partidoSigla ?: siglaFallback,
                            partidoNumero = partidoNum
                        )
                    }
                }
                cargo.copy(votosCandidatos = votosEnriquecidos)
            }
            eleicao.copy(cargos = cargosEnriquecidos)
        }
        bu.copy(eleicoes = eleicoesEnriquecidas)
    }

    /**
     * Formatação correta para pluralização de seções: "1 seção" vs "X seções"
     */
    fun formatarTotalSecoes(quantidade: Int): String {
        return if (quantidade == 1) "1 seção" else "$quantidade seções"
    }

    /**
     * Agrega votos de múltiplos boletins para uma eleição específica
     */
    suspend fun agregarResultados(
        boletins: List<BoletimUrna>,
        filtroUf: String = "TODOS",
        filtroCargo: Int? = null
    ): EleicaoAgregada = withContext(Dispatchers.IO) {
        val busFiltrados = if (filtroUf != "TODOS") {
            boletins.filter { it.uf.equals(filtroUf, ignoreCase = true) }
        } else {
            boletins
        }

        val anoEleicao = busFiltrados.firstOrNull()?.anoEleicao ?: 2026
        candidatoRepository.precarregarCacheSeNecessario(anoEleicao)

        val totalAptos = busFiltrados.sumOf { it.aptos }
        val totalComparecimento = busFiltrados.sumOf { it.comparecimento }
        val totalFaltas = busFiltrados.sumOf { it.faltas }
        val totalSecoes = busFiltrados.size

        // Agrupamento por código de cargo
        val cargosMap = mutableMapOf<Int, MutableList<CargoVotacao>>()
        for (bu in busFiltrados) {
            for (eleicao in bu.eleicoes) {
                for (cargo in eleicao.cargos) {
                    if (filtroCargo == null || cargo.codigoCargo == filtroCargo) {
                        cargosMap.getOrPut(cargo.codigoCargo) { mutableListOf() }.add(cargo)
                    }
                }
            }
        }

        val cargosAgregados = cargosMap.map { (codCargo, listaCargos) ->
            val nomeCargo = CargoNomes.getNomeCargo(codCargo)
            val tipoCargo = listaCargos.firstOrNull()?.tipoCargo ?: 0
            val totalNominais = listaCargos.sumOf { it.votosNominais }
            val totalLegenda = listaCargos.sumOf { it.votosLegenda }
            val totalBranco = listaCargos.sumOf { it.votosBranco }
            val totalNulos = listaCargos.sumOf { it.votosNulos }
            val totalVotosCargo = totalNominais + totalLegenda + totalBranco + totalNulos
            val votosValidos = totalNominais + totalLegenda

            // Agrupa votos por candidato
            val votosPorCandidato = mutableMapOf<String, VotoCandidato>()
            for (cargoItem in listaCargos) {
                for (voto in cargoItem.votosCandidatos) {
                    val existente = votosPorCandidato[voto.numero]
                    if (existente == null) {
                        votosPorCandidato[voto.numero] = voto.copy()
                    } else {
                        votosPorCandidato[voto.numero] = existente.copy(
                            votos = existente.votos + voto.votos,
                            nomeUrna = existente.nomeUrna ?: voto.nomeUrna,
                            partidoSigla = existente.partidoSigla ?: voto.partidoSigla
                        )
                    }
                }
            }

            // Garante resolução dos nomes oficiais de todos os candidatos
            val listaCandidatosFinal = votosPorCandidato.values.map { voto ->
                val resolvido = if (voto.nomeUrna == null || voto.nomeUrna!!.startsWith("Candidato")) {
                    candidatoRepository.resolverCandidato(
                        ano = busFiltrados.firstOrNull()?.anoEleicao ?: 2026,
                        cargo = codCargo,
                        uf = filtroUf,
                        numero = voto.numero
                    )
                } else null

                val nome = resolvido?.first ?: voto.nomeUrna ?: "Candidato ${voto.numero}"
                val sigla = resolvido?.second ?: voto.partidoSigla ?: (voto.numero.take(2).let { "Partido $it" })
                val pct = if (votosValidos > 0) (voto.votos.toFloat() / votosValidos) * 100f else 0f

                voto.copy(
                    nomeUrna = nome,
                    partidoSigla = sigla,
                    percentual = pct
                )
            }.sortedByDescending { it.votos }

            // Verifica projeção de 2º Turno caso seja Presidente ou Governador no 1º turno
            val turnoAtual = busFiltrados.firstOrNull()?.turno ?: 1
            val projecaoSegundoTurno = if (turnoAtual == 1 && CargoNomes.permiteSegundoTurno(codCargo) && listaCandidatosFinal.isNotEmpty()) {
                val primeiro = listaCandidatosFinal.getOrNull(0)
                val segundo = listaCandidatosFinal.getOrNull(1)
                val pctPrimeiro = primeiro?.percentual ?: 0f
                val pctSegundo = segundo?.percentual ?: 0f

                if (primeiro != null && segundo != null && pctPrimeiro <= 50.0f) {
                    SegundoTurnoProjecao(
                        haveraSegundoTurno = true,
                        primeiroColocado = primeiro,
                        segundoColocado = segundo,
                        percentualPrimeiro = pctPrimeiro,
                        percentualSegundo = pctSegundo,
                        mensagem = "Projeção de 2º Turno entre ${primeiro.nomeUrna} (${"%.1f".format(pctPrimeiro)}%) e ${segundo.nomeUrna} (${"%.1f".format(pctSegundo)}%)"
                    )
                } else if (primeiro != null && pctPrimeiro > 50.0f) {
                    SegundoTurnoProjecao(
                        haveraSegundoTurno = false,
                        primeiroColocado = primeiro,
                        segundoColocado = segundo,
                        percentualPrimeiro = pctPrimeiro,
                        percentualSegundo = pctSegundo,
                        mensagem = "Eleito em 1º Turno com ${"%.1f".format(pctPrimeiro)}% dos votos válidos"
                    )
                } else {
                    null
                }
            } else {
                null
            }

            CargoAgregado(
                codigoCargo = codCargo,
                nomeCargo = nomeCargo,
                tipoCargo = tipoCargo,
                votosCandidatos = listaCandidatosFinal,
                votosLegenda = totalLegenda,
                votosNominais = totalNominais,
                votosValidos = votosValidos,
                votosBranco = totalBranco,
                votosNulos = totalNulos,
                totalVotosCargo = totalVotosCargo,
                projecaoSegundoTurno = projecaoSegundoTurno
            )
        }.sortedBy { it.codigoCargo }

        EleicaoAgregada(
            totalSecoes = totalSecoes,
            totalAptos = totalAptos,
            totalComparecimento = totalComparecimento,
            totalFaltas = totalFaltas,
            cargos = cargosAgregados
        )
    }

    // -------------------------------------------------------------
    // Serialização JSON para Room
    // -------------------------------------------------------------

    private fun boletimParaEntity(bu: BoletimUrna): BuEntity {
        val root = JSONObject()
        root.put("quadro", bu.quadro)
        root.put("totalQuadros", bu.totalQuadros)
        root.put("versaoQr", bu.versaoQr)
        root.put("origem", bu.origem)
        root.put("processo", bu.processo)
        root.put("dataPleito", bu.dataPleito)
        root.put("pleito", bu.pleito)
        root.put("turno", bu.turno)
        root.put("fase", bu.fase)
        root.put("uf", bu.uf)
        root.put("municipio", bu.municipio)
        root.put("municipioNome", bu.municipioNome)
        root.put("zona", bu.zona)
        root.put("secao", bu.secao)
        root.put("idUe", bu.idUe)
        root.put("idCarga", bu.idCarga)
        root.put("versaoSoftware", bu.versaoSoftware)
        root.put("localVotacao", bu.localVotacao)
        root.put("aptos", bu.aptos)
        root.put("comparecimento", bu.comparecimento)
        root.put("faltas", bu.faltas)
        root.put("dataAbertura", bu.dataAbertura)
        root.put("horaAbertura", bu.horaAbertura)
        root.put("dataFechamento", bu.dataFechamento)
        root.put("horaFechamento", bu.horaFechamento)

        val eleicoesArr = JSONArray()
        for (eleicao in bu.eleicoes) {
            val elObj = JSONObject()
            elObj.put("idEleicao", eleicao.idEleicao)
            val cargosArr = JSONArray()
            for (cargo in eleicao.cargos) {
                val cObj = JSONObject()
                cObj.put("codigoCargo", cargo.codigoCargo)
                cObj.put("nomeCargo", cargo.nomeCargo)
                cObj.put("tipoCargo", cargo.tipoCargo)
                cObj.put("versaoCargo", cargo.versaoCargo)
                cObj.put("votosLegenda", cargo.votosLegenda)
                cObj.put("votosNominais", cargo.votosNominais)
                cObj.put("votosBranco", cargo.votosBranco)
                cObj.put("votosNulos", cargo.votosNulos)
                cObj.put("totalVotosCargo", cargo.totalVotosCargo)

                val vtsArr = JSONArray()
                for (voto in cargo.votosCandidatos) {
                    val vObj = JSONObject()
                    vObj.put("numero", voto.numero)
                    vObj.put("votos", voto.votos)
                    vObj.put("nomeUrna", voto.nomeUrna)
                    vObj.put("partidoSigla", voto.partidoSigla)
                    vObj.put("partidoNumero", voto.partidoNumero)
                    vtsArr.put(vObj)
                }
                cObj.put("votosCandidatos", vtsArr)
                cargosArr.put(cObj)
            }
            elObj.put("cargos", cargosArr)
            eleicoesArr.put(elObj)
        }
        root.put("eleicoes", eleicoesArr)

        return BuEntity(
            id = bu.id,
            ano = bu.anoEleicao,
            pleito = bu.pleito,
            turno = bu.turno,
            uf = bu.uf,
            municipio = bu.municipio,
            municipioNome = bu.municipioNome,
            zona = bu.zona,
            secao = bu.secao,
            aptos = bu.aptos,
            comparecimento = bu.comparecimento,
            faltas = bu.faltas,
            dataHoraAbertura = "${bu.dataAbertura} ${bu.horaAbertura}",
            dataHoraFechamento = "${bu.dataFechamento} ${bu.horaFechamento}",
            rawJson = root.toString(),
            hashSha512 = bu.hashSha512,
            assinatura = bu.assinatura,
            assinaturaValida = bu.assinaturaValida,
            criadoEm = bu.criadoEm
        )
    }

    private fun entityParaBoletim(entity: BuEntity): BoletimUrna {
        return try {
            val root = JSONObject(entity.rawJson)
            val eleicoesList = mutableListOf<EleicaoVotacao>()
            val eleicoesArr = root.optJSONArray("eleicoes") ?: JSONArray()

            for (i in 0 until eleicoesArr.length()) {
                val elObj = eleicoesArr.getJSONObject(i)
                val idEl = elObj.getString("idEleicao")
                val cargosArr = elObj.optJSONArray("cargos") ?: JSONArray()
                val cargosList = mutableListOf<CargoVotacao>()

                for (j in 0 until cargosArr.length()) {
                    val cObj = cargosArr.getJSONObject(j)
                    val vtsArr = cObj.optJSONArray("votosCandidatos") ?: JSONArray()
                    val vtsList = mutableListOf<VotoCandidato>()

                    for (k in 0 until vtsArr.length()) {
                        val vObj = vtsArr.getJSONObject(k)
                        vtsList.add(
                            VotoCandidato(
                                numero = vObj.getString("numero"),
                                votos = vObj.getInt("votos"),
                                nomeUrna = vObj.optString("nomeUrna").takeIf { it.isNotBlank() },
                                partidoSigla = vObj.optString("partidoSigla").takeIf { it.isNotBlank() },
                                partidoNumero = if (vObj.has("partidoNumero")) vObj.getInt("partidoNumero") else null
                            )
                        )
                    }

                    cargosList.add(
                        CargoVotacao(
                            codigoCargo = cObj.getInt("codigoCargo"),
                            nomeCargo = cObj.optString("nomeCargo", CargoNomes.getNomeCargo(cObj.getInt("codigoCargo"))),
                            tipoCargo = cObj.optInt("tipoCargo", 0),
                            versaoCargo = cObj.optString("versaoCargo", ""),
                            votosCandidatos = vtsList,
                            votosLegenda = cObj.optInt("votosLegenda", 0),
                            votosNominais = cObj.optInt("votosNominais", 0),
                            votosBranco = cObj.optInt("votosBranco", 0),
                            votosNulos = cObj.optInt("votosNulos", 0),
                            totalVotosCargo = cObj.optInt("totalVotosCargo", 0)
                        )
                    )
                }

                eleicoesList.add(EleicaoVotacao(idEleicao = idEl, cargos = cargosList))
            }

            BoletimUrna(
                id = entity.id,
                quadro = root.optInt("quadro", 1),
                totalQuadros = root.optInt("totalQuadros", 1),
                versaoQr = root.optString("versaoQr", "1.4"),
                origem = root.optString("origem", "VOTA"),
                processo = root.optString("processo", "1000"),
                dataPleito = root.optString("dataPleito", entity.ano.toString()),
                pleito = root.optString("pleito", entity.pleito),
                turno = entity.turno,
                fase = root.optString("fase", "O"),
                uf = entity.uf,
                municipio = entity.municipio,
                municipioNome = entity.municipioNome,
                zona = entity.zona,
                secao = entity.secao,
                idUe = root.optString("idUe", ""),
                idCarga = root.optString("idCarga", ""),
                versaoSoftware = root.optString("versaoSoftware", ""),
                localVotacao = root.optString("localVotacao", ""),
                aptos = entity.aptos,
                comparecimento = entity.comparecimento,
                faltas = entity.faltas,
                dataAbertura = root.optString("dataAbertura", ""),
                horaAbertura = root.optString("horaAbertura", ""),
                dataFechamento = root.optString("dataFechamento", ""),
                horaFechamento = root.optString("horaFechamento", ""),
                eleicoes = eleicoesList,
                hashSha512 = entity.hashSha512,
                assinatura = entity.assinatura,
                assinaturaValida = entity.assinaturaValida,
                criadoEm = entity.criadoEm
            )
        } catch (e: Exception) {
            // Em caso de erro na desserialização, cria modelo básico
            BoletimUrna(
                id = entity.id,
                pleito = entity.pleito,
                turno = entity.turno,
                uf = entity.uf,
                municipio = entity.municipio,
                municipioNome = entity.municipioNome,
                zona = entity.zona,
                secao = entity.secao,
                aptos = entity.aptos,
                comparecimento = entity.comparecimento,
                faltas = entity.faltas,
                criadoEm = entity.criadoEm
            )
        }
    }
}

data class EleicaoAgregada(
    val totalSecoes: Int,
    val totalAptos: Int,
    val totalComparecimento: Int,
    val totalFaltas: Int,
    val cargos: List<CargoAgregado>
) {
    val taxaComparecimento: Float
        get() = if (totalAptos > 0) (totalComparecimento.toFloat() / totalAptos) * 100f else 0f

    val taxaAbstencao: Float
        get() = if (totalAptos > 0) (totalFaltas.toFloat() / totalAptos) * 100f else 0f
}

data class CargoAgregado(
    val codigoCargo: Int,
    val nomeCargo: String,
    val tipoCargo: Int,
    val votosCandidatos: List<VotoCandidato>,
    val votosLegenda: Int,
    val votosNominais: Int,
    val votosValidos: Int,
    val votosBranco: Int,
    val votosNulos: Int,
    val totalVotosCargo: Int,
    val projecaoSegundoTurno: SegundoTurnoProjecao?
)
