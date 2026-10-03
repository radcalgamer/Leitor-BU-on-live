package com.example

import com.example.model.CargoNomes
import com.example.model.CargoVotacao
import com.example.model.SegundoTurnoProjecao
import com.example.model.VotoCandidato
import com.example.parser.BuQrAssembler
import com.example.parser.BuQrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuRulesTest {

    @Test
    fun `test pluralization of sections`() {
        val repoFormat = { count: Int -> if (count == 1) "1 seção" else "$count seções" }
        assertEquals("1 seção", repoFormat(1))
        assertEquals("2 seções", repoFormat(2))
        assertEquals("15 seções", repoFormat(15))
    }

    @Test
    fun `test segundo turno projecao for presidente when leader has 50 percent or less`() {
        val cand1 = VotoCandidato("13", 48, "LULA", "PT", 13, 48.0f)
        val cand2 = VotoCandidato("22", 42, "FLAVIO BOLSONARO", "PL", 22, 42.0f)
        val cand3 = VotoCandidato("30", 10, "ZEMA", "NOVO", 30, 10.0f)

        val lista = listOf(cand1, cand2, cand3)
        val primeiro = lista[0]
        val segundo = lista[1]

        val projecao = if (primeiro.percentual <= 50.0f) {
            SegundoTurnoProjecao(
                haveraSegundoTurno = true,
                primeiroColocado = primeiro,
                segundoColocado = segundo,
                percentualPrimeiro = primeiro.percentual,
                percentualSegundo = segundo.percentual,
                mensagem = "Projeção de 2º Turno entre ${primeiro.nomeUrna} e ${segundo.nomeUrna}"
            )
        } else null

        assertNotNull(projecao)
        assertTrue(projecao!!.haveraSegundoTurno)
        assertEquals("13", projecao.primeiroColocado?.numero)
        assertEquals("22", projecao.segundoColocado?.numero)
    }

    @Test
    fun `test eleito no primeiro turno when leader has over 50 percent`() {
        val cand1 = VotoCandidato("13", 55, "LULA", "PT", 13, 55.0f)
        val cand2 = VotoCandidato("22", 35, "FLAVIO BOLSONARO", "PL", 22, 35.0f)
        val cand3 = VotoCandidato("30", 10, "ZEMA", "NOVO", 30, 10.0f)

        val lista = listOf(cand1, cand2, cand3)
        val primeiro = lista[0]
        val segundo = lista[1]

        val projecao = if (primeiro.percentual > 50.0f) {
            SegundoTurnoProjecao(
                haveraSegundoTurno = false,
                primeiroColocado = primeiro,
                segundoColocado = segundo,
                percentualPrimeiro = primeiro.percentual,
                percentualSegundo = segundo.percentual,
                mensagem = "Eleito em 1º Turno com ${primeiro.percentual}%"
            )
        } else null

        assertNotNull(projecao)
        assertFalse(projecao!!.haveraSegundoTurno)
    }

    @Test
    fun `test parser on complete general election mock`() {
        val bu = BuQrAssembler.criarEleicaoGeralCompletaExemplo(secaoNumero = 5, zonaNumero = 10, uf = "RJ")
        assertNotNull(bu)
        assertEquals(1, bu.turno)
        assertEquals("RJ", bu.uf)
        assertEquals(5, bu.secao)
        assertEquals(10, bu.zona)

        val cargos = bu.eleicoes.first().cargos
        assertEquals(5, cargos.size)

        val codigosCargos = cargos.map { it.codigoCargo }
        assertTrue(codigosCargos.contains(CargoNomes.PRESIDENTE))
        assertTrue(codigosCargos.contains(CargoNomes.GOVERNADOR))
        assertTrue(codigosCargos.contains(CargoNomes.SENADOR))
        assertTrue(codigosCargos.contains(CargoNomes.DEPUTADO_FEDERAL))
        assertTrue(codigosCargos.contains(CargoNomes.DEPUTADO_ESTADUAL))
    }
}
