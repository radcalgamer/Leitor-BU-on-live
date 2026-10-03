package com.eleitorix.parser

import com.eleitorix.model.BoletimUrna
import com.eleitorix.model.CargoNomes
import com.eleitorix.model.CargoVotacao
import com.eleitorix.model.EleicaoVotacao
import com.eleitorix.model.VotoCandidato

/**
 * Parser para o formato oficial de QR Code de Boletim de Urna (TSE).
 * Suporta tanto QR codes de quadro único quanto texto unificado de múltiplos quadros.
 */
object BuQrParser {

    fun parse(rawText: String): BoletimUrna? {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("QRBU:")) {
            return null
        }

        val tokens = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var quadro = 1
        var totalQuadros = 1
        var versaoQr = "1.4"
        var origem = "VOTA"
        var processo = "1000"
        var dataPleito = ""
        var pleito = ""
        var turno = 1
        var fase = "O"
        var uf = "BR"
        var municipio = ""
        var zona = 0
        var secao = 0
        var idUe = ""
        var idCarga = ""
        var versaoSoftware = ""
        var localVotacao = ""
        var aptos = 0
        var comparecimento = 0
        var faltas = 0
        var dataAbertura = ""
        var horaAbertura = ""
        var dataFechamento = ""
        var horaFechamento = ""
        var hashSha512: String? = null
        var assinatura: String? = null

        val eleicoesList = mutableListOf<EleicaoVotacao>()
        var currentIdEleicao: String? = null
        var currentCargos = mutableListOf<CargoVotacao>()
        var currentCargoCodigo = 0
        var currentTipoCargo = 0
        var currentVersaoCargo = ""
        var currentVotosCandidatos = mutableListOf<VotoCandidato>()
        var currentVotosLegenda = 0
        var currentVotosNominais = 0
        var currentVotosBranco = 0
        var currentVotosNulos = 0
        var currentTotalCargo = 0
        var hasActiveCargo = false

        fun flushCurrentCargo() {
            if (hasActiveCargo && currentCargoCodigo > 0) {
                val nomeCargo = CargoNomes.getNomeCargo(currentCargoCodigo)
                val cargo = CargoVotacao(
                    codigoCargo = currentCargoCodigo,
                    nomeCargo = nomeCargo,
                    tipoCargo = currentTipoCargo,
                    versaoCargo = currentVersaoCargo,
                    votosCandidatos = currentVotosCandidatos.toList(),
                    votosLegenda = currentVotosLegenda,
                    votosNominais = if (currentVotosNominais > 0) currentVotosNominais else currentVotosCandidatos.sumOf { it.votos },
                    votosBranco = currentVotosBranco,
                    votosNulos = currentVotosNulos,
                    totalVotosCargo = if (currentTotalCargo > 0) currentTotalCargo else (currentVotosCandidatos.sumOf { it.votos } + currentVotosBranco + currentVotosNulos)
                )
                currentCargos.add(cargo)
                currentVotosCandidatos = mutableListOf()
                currentVotosLegenda = 0
                currentVotosNominais = 0
                currentVotosBranco = 0
                currentVotosNulos = 0
                currentTotalCargo = 0
                hasActiveCargo = false
            }
        }

        fun flushCurrentEleicao() {
            flushCurrentCargo()
            if (currentIdEleicao != null && currentCargos.isNotEmpty()) {
                eleicoesList.add(EleicaoVotacao(idEleicao = currentIdEleicao!!, cargos = currentCargos.toList()))
                currentCargos = mutableListOf()
                currentIdEleicao = null
            }
        }

        for (token in tokens) {
            when {
                token.startsWith("QRBU:") -> {
                    val parts = token.removePrefix("QRBU:").split(":")
                    if (parts.size >= 2) {
                        quadro = parts[0].toIntOrNull() ?: 1
                        totalQuadros = parts[1].toIntOrNull() ?: 1
                    }
                }
                token.startsWith("VRQR:") -> versaoQr = token.removePrefix("VRQR:")
                token.startsWith("ORIG:") -> origem = token.removePrefix("ORIG:")
                token.startsWith("PROC:") -> processo = token.removePrefix("PROC:")
                token.startsWith("DTPL:") -> dataPleito = token.removePrefix("DTPL:")
                token.startsWith("PLEI:") -> pleito = token.removePrefix("PLEI:")
                token.startsWith("TURN:") -> turno = token.removePrefix("TURN:").toIntOrNull() ?: 1
                token.startsWith("FASE:") -> fase = token.removePrefix("FASE:")
                token.startsWith("UNFE:") -> uf = token.removePrefix("UNFE:")
                token.startsWith("MUNI:") -> municipio = token.removePrefix("MUNI:")
                token.startsWith("ZONA:") -> zona = token.removePrefix("ZONA:").toIntOrNull() ?: 0
                token.startsWith("SECA:") -> secao = token.removePrefix("SECA:").toIntOrNull() ?: 0
                token.startsWith("IDUE:") -> idUe = token.removePrefix("IDUE:")
                token.startsWith("IDCA:") -> idCarga = token.removePrefix("IDCA:")
                token.startsWith("VERS:") -> versaoSoftware = token.removePrefix("VERS:")
                token.startsWith("LOCA:") -> localVotacao = token.removePrefix("LOCA:")
                token.startsWith("APTO:") -> aptos = token.removePrefix("APTO:").toIntOrNull() ?: 0
                token.startsWith("COMP:") -> comparecimento = token.removePrefix("COMP:").toIntOrNull() ?: 0
                token.startsWith("FALT:") -> faltas = token.removePrefix("FALT:").toIntOrNull() ?: 0
                token.startsWith("DTAB:") -> dataAbertura = token.removePrefix("DTAB:")
                token.startsWith("HRAB:") -> horaAbertura = token.removePrefix("HRAB:")
                token.startsWith("DTFC:") -> dataFechamento = token.removePrefix("DTFC:")
                token.startsWith("HRFC:") -> horaFechamento = token.removePrefix("HRFC:")
                token.startsWith("IDEL:") -> {
                    flushCurrentEleicao()
                    currentIdEleicao = token.removePrefix("IDEL:")
                }
                token.startsWith("CARG:") -> {
                    flushCurrentCargo()
                    currentCargoCodigo = token.removePrefix("CARG:").toIntOrNull() ?: 0
                    hasActiveCargo = true
                    if (currentIdEleicao == null) {
                        currentIdEleicao = pleito.ifEmpty { "1" }
                    }
                }
                token.startsWith("TIPO:") -> currentTipoCargo = token.removePrefix("TIPO:").toIntOrNull() ?: 0
                token.startsWith("VERC:") -> currentVersaoCargo = token.removePrefix("VERC:")
                token.startsWith("LEGP:") -> currentVotosLegenda += token.removePrefix("LEGP:").toIntOrNull() ?: 0
                token.startsWith("LEGC:") -> currentVotosLegenda += token.removePrefix("LEGC:").toIntOrNull() ?: 0
                token.startsWith("NOMI:") -> currentVotosNominais = token.removePrefix("NOMI:").toIntOrNull() ?: 0
                token.startsWith("BRAN:") -> currentVotosBranco = token.removePrefix("BRAN:").toIntOrNull() ?: 0
                token.startsWith("NULO:") -> currentVotosNulos = token.removePrefix("NULO:").toIntOrNull() ?: 0
                token.startsWith("TOTC:") -> currentTotalCargo = token.removePrefix("TOTC:").toIntOrNull() ?: 0
                token.startsWith("HASH:") -> hashSha512 = token.removePrefix("HASH:")
                token.startsWith("ASSI:") -> assinatura = token.removePrefix("ASSI:")
                token.startsWith("PART:") -> {
                    // Partido prefix, votos candidatos virão nos próximos tokens
                }
                token.contains(":") -> {
                    // formato candidato:votos (ex: 13:45, 22:38, 91001:1)
                    val parts = token.split(":")
                    if (parts.size == 2) {
                        val num = parts[0]
                        val vts = parts[1].toIntOrNull()
                        if (vts != null && num.all { it.isDigit() }) {
                            currentVotosCandidatos.add(VotoCandidato(numero = num, votos = vts))
                        }
                    }
                }
            }
        }

        flushCurrentEleicao()

        return BoletimUrna(
            quadro = quadro,
            totalQuadros = totalQuadros,
            versaoQr = versaoQr,
            origem = origem,
            processo = processo,
            dataPleito = dataPleito,
            pleito = pleito,
            turno = turno,
            fase = fase,
            uf = uf,
            municipio = municipio,
            zona = zona,
            secao = secao,
            idUe = idUe,
            idCarga = idCarga,
            versaoSoftware = versaoSoftware,
            localVotacao = localVotacao,
            aptos = aptos,
            comparecimento = comparecimento,
            faltas = if (faltas > 0) faltas else (aptos - comparecimento).coerceAtLeast(0),
            dataAbertura = dataAbertura,
            horaAbertura = horaAbertura,
            dataFechamento = dataFechamento,
            horaFechamento = horaFechamento,
            eleicoes = eleicoesList,
            hashSha512 = hashSha512,
            assinatura = assinatura
        )
    }
}
