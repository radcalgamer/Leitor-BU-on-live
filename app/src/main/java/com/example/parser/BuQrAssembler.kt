package com.example.parser

import com.example.crypto.BuSignatureValidator
import com.example.model.BoletimUrna
import com.example.model.CargoNomes
import com.example.model.CargoVotacao
import com.example.model.EleicaoVotacao
import com.example.model.VotoCandidato

/**
 * Montador e validador de múltiplos QR Codes do Boletim de Urna.
 */
class BuQrAssembler {

    private val quadrosRecebidos = mutableMapOf<Int, String>()
    var totalQuadrosEsperados: Int = 1
        private set

    fun getQuadrosRecebidosCount(): Int = quadrosRecebidos.size

    fun getIndicesRecebidos(): List<Int> = quadrosRecebidos.keys.sorted()

    fun reset() {
        quadrosRecebidos.clear()
        totalQuadrosEsperados = 1
    }

    /**
     * Adiciona um quadro lido.
     * Retorna um Pair: (isCompleto: Boolean, resultadoBU: BoletimUrna?)
     */
    fun adicionarQuadro(textoQuadro: String): Pair<Boolean, BoletimUrna?> {
        val trimmed = textoQuadro.trim()
        if (!trimmed.startsWith("QRBU:")) return Pair(false, null)

        val headerEnd = trimmed.indexOf(" ")
        val header = if (headerEnd != -1) trimmed.substring(0, headerEnd) else trimmed
        val parts = header.removePrefix("QRBU:").split(":")

        if (parts.size >= 2) {
            val indice = parts[0].toIntOrNull() ?: 1
            val total = parts[1].toIntOrNull() ?: 1
            totalQuadrosEsperados = total
            quadrosRecebidos[indice] = trimmed

            if (quadrosRecebidos.size >= totalQuadrosEsperados) {
                // Todos os quadros foram recebidos! Montar o texto completo
                val buMontado = montarBoletimCompleto()
                return Pair(true, buMontado)
            }
        } else {
            // Quadro único simples
            val bu = BuQrParser.parse(trimmed)
            return Pair(bu != null, bu)
        }

        return Pair(false, null)
    }

    private fun montarBoletimCompleto(): BoletimUrna? {
        val indices = (1..totalQuadrosEsperados).toList()
        val builder = java.lang.StringBuilder()

        var ultimoHash: String? = null
        var ultimaAssinatura: String? = null

        for (i in indices) {
            val conteudo = quadrosRecebidos[i] ?: return null
            if (i == 1) {
                // Quadro 1 contém todo o cabeçalho inicial até IDEL/CARG
                builder.append(conteudo)
            } else {
                // Quadros subsequentes começam com "QRBU:X:Y VRQR:1.4 "
                // Removemos o prefixo do quadro para continuar os dados
                val clean = conteudo.replaceFirst("^QRBU:\\d+:\\d+\\s+VRQR:[^\\s]+\\s+".toRegex(), "")
                builder.append(" ").append(clean)
            }

            // Extrai o hash e assinatura do último quadro
            val hashMatch = "HASH:([0-9A-Fa-f]+)".toRegex().find(conteudo)
            if (hashMatch != null) {
                ultimoHash = hashMatch.groupValues[1]
            }
            val assiMatch = "ASSI:([0-9A-Fa-f]+)".toRegex().find(conteudo)
            if (assiMatch != null) {
                ultimaAssinatura = assiMatch.groupValues[1]
            }
        }

        val textoUnificado = builder.toString()
        val bu = BuQrParser.parse(textoUnificado) ?: return null

        val assinaturaOk = if (ultimoHash != null && ultimaAssinatura != null) {
            BuSignatureValidator.validarAssinatura(ultimoHash, ultimaAssinatura)
        } else {
            true
        }

        return bu.copy(
            hashSha512 = ultimoHash,
            assinatura = ultimaAssinatura,
            assinaturaValida = assinaturaOk
        )
    }

    companion object {
        /**
         * Gera uma eleição geral completa (Presidente, Governador, Senador,
         * Deputado Federal, Deputado Estadual) realista para demonstração e testes,
         * atendendo ao pedido do usuário.
         */
        fun criarEleicaoGeralCompletaExemplo(
            secaoNumero: Int = 1,
            zonaNumero: Int = 8,
            uf: String = "RJ",
            municipioNome: String = "RIO DE JANEIRO"
        ): BoletimUrna {
            val aptos = 450
            val comparecimento = 385
            val faltas = aptos - comparecimento

            // 1. Presidente (Majoritário Nacional)
            val presidente = CargoVotacao(
                codigoCargo = CargoNomes.PRESIDENTE,
                nomeCargo = CargoNomes.getNomeCargo(CargoNomes.PRESIDENTE),
                tipoCargo = 0,
                versaoCargo = "2026",
                votosCandidatos = listOf(
                    VotoCandidato("13", 182, "LULA", "PT", 13),
                    VotoCandidato("22", 158, "FLAVIO BOLSONARO", "PL", 22),
                    VotoCandidato("30", 24, "ZEMA", "NOVO", 30),
                    VotoCandidato("55", 9, "RONALDO CAIADO", "PSD", 55),
                    VotoCandidato("14", 4, "RENAN SANTOS", "MISSÃO", 14),
                    VotoCandidato("80", 2, "SAMARA", "UP", 80)
                ),
                votosNominais = 379,
                votosLegenda = 0,
                votosBranco = 3,
                votosNulos = 3,
                totalVotosCargo = 385
            )

            // 2. Governador (Majoritário Estadual)
            val governador = CargoVotacao(
                codigoCargo = CargoNomes.GOVERNADOR,
                nomeCargo = CargoNomes.getNomeCargo(CargoNomes.GOVERNADOR),
                tipoCargo = 0,
                versaoCargo = "2026",
                votosCandidatos = listOf(
                    VotoCandidato("55", 175, "EDUARDO PAES", "PSD", 55),
                    VotoCandidato("22", 142, "DOUGLAS RUAS", "PL", 22),
                    VotoCandidato("10", 31, "GAROTINHO", "REPUBLICANOS", 10),
                    VotoCandidato("30", 18, "ANDRÉ MARINHO", "NOVO", 30),
                    VotoCandidato("50", 10, "WILLIAM SIRI", "PSOL", 50)
                ),
                votosNominais = 376,
                votosLegenda = 0,
                votosBranco = 4,
                votosNulos = 5,
                totalVotosCargo = 385
            )

            // 3. Senador (Majoritário Estadual)
            val senador = CargoVotacao(
                codigoCargo = CargoNomes.SENADOR,
                nomeCargo = CargoNomes.getNomeCargo(CargoNomes.SENADOR),
                tipoCargo = 0,
                versaoCargo = "2026",
                votosCandidatos = listOf(
                    VotoCandidato("555", 145, "PEDRO PAULO", "PSD", 55),
                    VotoCandidato("222", 138, "CARLOS PORTINHO", "PL", 22),
                    VotoCandidato("131", 62, "BENEDITA DA SILVA", "PT", 13),
                    VotoCandidato("100", 21, "MARCELO CRIVELLA", "REPUBLICANOS", 10),
                    VotoCandidato("500", 11, "MONICA BENICIO", "PSOL", 50)
                ),
                votosNominais = 377,
                votosLegenda = 0,
                votosBranco = 5,
                votosNulos = 3,
                totalVotosCargo = 385
            )

            // 4. Deputado Federal (Proporcional)
            val deputadoFederal = CargoVotacao(
                codigoCargo = CargoNomes.DEPUTADO_FEDERAL,
                nomeCargo = CargoNomes.getNomeCargo(CargoNomes.DEPUTADO_FEDERAL),
                tipoCargo = 1,
                versaoCargo = "2026",
                votosCandidatos = listOf(
                    VotoCandidato("2212", 48, "GENERAL PAZUELLO", "PL", 22),
                    VotoCandidato("1300", 42, "LINDBERGH", "PT", 13),
                    VotoCandidato("5000", 39, "TARCÍSIO MOTTA", "PSOL", 50),
                    VotoCandidato("5588", 35, "DANIEL SORANZ", "PSD", 55),
                    VotoCandidato("1177", 33, "DR. LUIZINHO", "PP", 11),
                    VotoCandidato("5077", 29, "TALÍRIA PETRONE", "PSOL", 50),
                    VotoCandidato("2227", 28, "THIAGO GAGLIASSO", "PL", 22),
                    VotoCandidato("3030", 25, "LUIZ LIMA", "NOVO", 30),
                    VotoCandidato("1330", 22, "ANIELLE FRANCO", "PT", 13),
                    VotoCandidato("4500", 19, "MARCELO QUEIROZ", "PSDB", 45),
                    VotoCandidato("1516", 18, "GUTEMBERG REIS", "MDB", 15),
                    VotoCandidato("4040", 15, "CHICO RUBENS PAIVA", "PSB", 40)
                ),
                votosNominais = 353,
                votosLegenda = 19,
                votosBranco = 6,
                votosNulos = 7,
                totalVotosCargo = 385
            )

            // 5. Deputado Estadual (Proporcional)
            val deputadoEstadual = CargoVotacao(
                codigoCargo = CargoNomes.DEPUTADO_ESTADUAL,
                nomeCargo = CargoNomes.getNomeCargo(CargoNomes.DEPUTADO_ESTADUAL),
                tipoCargo = 1,
                versaoCargo = "2026",
                votosCandidatos = listOf(
                    VotoCandidato("44444", 51, "MÁRCIO CANELLA", "UNIÃO", 44),
                    VotoCandidato("22222", 44, "GUILHERME DELAROLI", "PL", 22),
                    VotoCandidato("50007", 38, "RENATA SOUZA", "PSOL", 50),
                    VotoCandidato("55555", 36, "GUILHERME SCHLEDER", "PSD", 55),
                    VotoCandidato("10123", 31, "TIA JU", "REPUBLICANOS", 10),
                    VotoCandidato("22322", 29, "ÍNDIA ARMELAU", "PL", 22),
                    VotoCandidato("13580", 26, "VERÔNICA LIMA", "PT", 13),
                    VotoCandidato("40000", 24, "CARLOS MINC", "PSB", 40),
                    VotoCandidato("15000", 22, "GIOVANI RATINHO", "MDB", 15),
                    VotoCandidato("12345", 19, "LEO LUPI", "PDT", 12),
                    VotoCandidato("22111", 17, "RENAN JORDY", "PL", 22),
                    VotoCandidato("30007", 15, "ALEXANDRE FREITAS", "NOVO", 30)
                ),
                votosNominais = 352,
                votosLegenda = 18,
                votosBranco = 8,
                votosNulos = 7,
                totalVotosCargo = 385
            )

            val eleicao = EleicaoVotacao(
                idEleicao = "1",
                cargos = listOf(presidente, governador, senador, deputadoFederal, deputadoEstadual)
            )

            return BoletimUrna(
                quadro = 1,
                totalQuadros = 1,
                versaoQr = "1.4",
                origem = "VOTA",
                processo = "1000",
                dataPleito = "20261004",
                pleito = "1100",
                turno = 1,
                fase = "O",
                uf = uf,
                municipio = "60011",
                municipioNome = municipioNome,
                zona = zonaNumero,
                secao = secaoNumero,
                idUe = "0194821",
                idCarga = "928374829104829103948201",
                versaoSoftware = "8.26.0.0",
                localVotacao = "ESCOLA MUNICIPAL BRASIL",
                aptos = aptos,
                comparecimento = comparecimento,
                faltas = faltas,
                dataAbertura = "20261004",
                horaAbertura = "080000",
                dataFechamento = "20261004",
                horaFechamento = "170000",
                eleicoes = listOf(eleicao),
                hashSha512 = "3CEEF49256BBABD427DBBFFDFFE7A1417B6CE617EDE22BEC881CAB54AE724140E1129C1D3FA882E8407F0111ED25557ED7B64E915F3D48CABA4D922C46A41A69",
                assinatura = "1CC03A8F61F77C8E22873A5F8C8E474AC5D303C022FBFFCB48A30FB589F632DD0A01744FDF9C703EFE053DE94BA81EE9C3B6F24F2ADD93EFD264BBE4B2648B00",
                assinaturaValida = true
            )
        }
    }
}
