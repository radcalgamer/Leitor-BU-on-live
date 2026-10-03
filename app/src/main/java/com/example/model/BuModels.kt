package com.example.model

/**
 * Modelos de dados para o Boletim de Urna (BU) e Candidatos
 * de acordo com as especificações do TSE (Tribunal Superior Eleitoral).
 */

data class BoletimUrna(
    val id: Long = 0,
    val quadro: Int = 1,
    val totalQuadros: Int = 1,
    val versaoQr: String = "1.4",
    val origem: String = "VOTA",
    val processo: String = "1000",
    val dataPleito: String = "",
    val pleito: String = "",
    val turno: Int = 1,
    val fase: String = "O", // O = Oficial, S = Simulado
    val uf: String = "BR",
    val municipio: String = "",
    val municipioNome: String? = null,
    val zona: Int = 0,
    val secao: Int = 0,
    val idUe: String = "",
    val idCarga: String = "",
    val versaoSoftware: String = "",
    val localVotacao: String = "",
    val aptos: Int = 0,
    val comparecimento: Int = 0,
    val faltas: Int = 0,
    val dataAbertura: String = "",
    val horaAbertura: String = "",
    val dataFechamento: String = "",
    val horaFechamento: String = "",
    val eleicoes: List<EleicaoVotacao> = emptyList(),
    val hashSha512: String? = null,
    val assinatura: String? = null,
    val assinaturaValida: Boolean? = null,
    val criadoEm: Long = System.currentTimeMillis()
) {
    val anoEleicao: Int
        get() = if (dataPleito.length >= 4) {
            dataPleito.substring(0, 4).toIntOrNull() ?: 2026
        } else {
            2026
        }

    val taxaComparecimento: Float
        get() = if (aptos > 0) (comparecimento.toFloat() / aptos) * 100f else 0f

    val taxaAbstencao: Float
        get() = if (aptos > 0) (faltas.toFloat() / aptos) * 100f else 0f
}

data class EleicaoVotacao(
    val idEleicao: String,
    val cargos: List<CargoVotacao> = emptyList()
)

data class CargoVotacao(
    val codigoCargo: Int,
    val nomeCargo: String,
    val tipoCargo: Int = 0, // 0 = Majoritário, 1 = Proporcional, 2 = Consulta
    val versaoCargo: String = "",
    val votosCandidatos: List<VotoCandidato> = emptyList(),
    val votosLegenda: Int = 0,
    val votosNominais: Int = 0,
    val votosBranco: Int = 0,
    val votosNulos: Int = 0,
    val totalVotosCargo: Int = 0
) {
    val votosValidos: Int
        get() = votosNominais + votosLegenda
}

data class VotoCandidato(
    val numero: String,
    val votos: Int,
    var nomeUrna: String? = null,
    var partidoSigla: String? = null,
    var partidoNumero: Int? = null,
    var percentual: Float = 0f
)

data class Candidato(
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

/**
 * Informações de projeção de 2º Turno
 */
data class SegundoTurnoProjecao(
    val haveraSegundoTurno: Boolean,
    val primeiroColocado: VotoCandidato?,
    val segundoColocado: VotoCandidato?,
    val percentualPrimeiro: Float,
    val percentualSegundo: Float,
    val mensagem: String
)

/**
 * Mapeamento e nomes oficiais dos cargos constitucionais segundo o bu.asn1 do TSE
 */
object CargoNomes {
    const val PRESIDENTE = 1
    const val VICE_PRESIDENTE = 2
    const val GOVERNADOR = 3
    const val VICE_GOVERNADOR = 4
    const val SENADOR = 5
    const val DEPUTADO_FEDERAL = 6
    const val DEPUTADO_ESTADUAL = 7
    const val DEPUTADO_DISTRITAL = 8
    const val SUPLENTE_1 = 9
    const val SUPLENTE_2 = 10
    const val PREFEITO = 11
    const val VICE_PREFEITO = 12
    const val VEREADOR = 13
    const val CONSULTA_1 = 51
    const val CONSULTA_2 = 64

    fun getNomeCargo(codigo: Int): String {
        return when (codigo) {
            PRESIDENTE -> "Presidente"
            VICE_PRESIDENTE -> "Vice-Presidente"
            GOVERNADOR -> "Governador"
            VICE_GOVERNADOR -> "Vice-Governador"
            SENADOR -> "Senador"
            DEPUTADO_FEDERAL -> "Deputado Federal"
            DEPUTADO_ESTADUAL -> "Deputado Estadual"
            DEPUTADO_DISTRITAL -> "Deputado Distrital"
            SUPLENTE_1 -> "1º Suplente"
            SUPLENTE_2 -> "2º Suplente"
            PREFEITO -> "Prefeito"
            VICE_PREFEITO -> "Vice-Prefeito"
            VEREADOR -> "Vereador"
            CONSULTA_1 -> "Consulta Popular 1"
            CONSULTA_2 -> "Consulta Popular 2"
            else -> "Cargo $codigo"
        }
    }

    /**
     * Retorna se o cargo tem previsão constitucional de 2º Turno caso o líder
     * não atinja mais de 50% dos votos válidos (Presidente e Governador).
     */
    fun permiteSegundoTurno(codigoCargo: Int): Boolean {
        return codigoCargo == PRESIDENTE || codigoCargo == GOVERNADOR
    }

    /**
     * Escopo de apuração do cargo
     */
    fun escopoDoCargo(codigo: Int): String {
        return when (codigo) {
            PRESIDENTE, VICE_PRESIDENTE -> "Nacional"
            GOVERNADOR, VICE_GOVERNADOR, SENADOR, DEPUTADO_FEDERAL, DEPUTADO_ESTADUAL, DEPUTADO_DISTRITAL -> "Estadual"
            PREFEITO, VICE_PREFEITO, VEREADOR -> "Municipal"
            else -> "Outro"
        }
    }
}
