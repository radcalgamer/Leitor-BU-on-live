package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.repository.BuRepository
import com.example.data.repository.CargoAgregado
import com.example.data.repository.EleicaoAgregada
import com.example.model.BoletimUrna
import com.example.model.CargoNomes
import com.example.parser.BuQrAssembler
import com.example.ui.components.CandidateVoteBar
import com.example.ui.components.PartyDistributionChart
import com.example.ui.components.SegundoTurnoCard
import com.example.ui.components.TurnoutDonutChart
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ResultadosScreen(
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { BuRepository(context) }
    val scope = rememberCoroutineScope()
    val boletins by repository.getBoletinsFlow().collectAsState(initial = emptyList())

    var filtroUf by remember { mutableStateOf("TODOS") }
    var filtroCargoSelecionado by remember { mutableStateOf<Int?>(null) }
    var eleicaoAgregada by remember { mutableStateOf<EleicaoAgregada?>(null) }

    // Atualiza agregação sempre que mudar boletins ou filtros
    LaunchedEffect(boletins, filtroUf, filtroCargoSelecionado) {
        if (boletins.isNotEmpty()) {
            eleicaoAgregada = repository.agregarResultados(
                boletins = boletins,
                filtroUf = filtroUf,
                filtroCargo = filtroCargoSelecionado
            )
        } else {
            eleicaoAgregada = null
        }
    }

    val ufsDisponiveis = remember(boletins) {
        listOf("TODOS") + boletins.map { it.uf }.distinct().sorted()
    }

    val cargosDisponiveis = remember(boletins) {
        boletins.flatMap { it.eleicoes }
            .flatMap { it.cargos }
            .map { it.codigoCargo }
            .distinct()
            .sorted()
    }

    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale("pt", "BR")) }

    Box(modifier = modifier.fillMaxSize()) {
        if (boletins.isEmpty()) {
            EmptyResultadosState(
                onNavigateToScanner = onNavigateToScanner,
                onCarregarExemploCompleto = {
                    scope.launch {
                        val buExemplo = BuQrAssembler.criarEleicaoGeralCompletaExemplo(
                            secaoNumero = 1,
                            zonaNumero = 8,
                            uf = "RJ",
                            municipioNome = "RIO DE JANEIRO"
                        )
                        repository.salvarBoletim(buExemplo)
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Barra de Status com Contagem Pluralizada Correta: "1 seção" vs "X seções"
                item {
                    val secoesTexto = repository.formatarTotalSecoes(eleicaoAgregada?.totalSecoes ?: 0)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Total Apurado: $secoesTexto",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Botão para navegar até o scanner e ler mais seções reais
                            Button(
                                onClick = onNavigateToScanner,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ler Seção",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Filtro por UF
                if (ufsDisponiveis.size > 2) {
                    item {
                        Column {
                            Text(
                                text = "Filtrar por Estado / UF:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ufsDisponiveis.forEach { uf ->
                                    FilterChip(
                                        selected = filtroUf == uf,
                                        onClick = { filtroUf = uf },
                                        label = { Text(if (uf == "TODOS") "Todos os Estados" else uf) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Filtro por Cargo
                if (cargosDisponiveis.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Filtrar por Cargo:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = filtroCargoSelecionado == null,
                                    onClick = { filtroCargoSelecionado = null },
                                    label = { Text("Todos os Cargos") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                                cargosDisponiveis.forEach { codCargo ->
                                    val nome = CargoNomes.getNomeCargo(codCargo)
                                    FilterChip(
                                        selected = filtroCargoSelecionado == codCargo,
                                        onClick = {
                                            filtroCargoSelecionado = if (filtroCargoSelecionado == codCargo) null else codCargo
                                        },
                                        label = { Text(nome) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Card de Comparecimento Geral
                eleicaoAgregada?.let { agregada ->
                    item {
                        TurnoutDonutChart(
                            aptos = agregada.totalAptos,
                            comparecimento = agregada.totalComparecimento,
                            faltas = agregada.totalFaltas
                        )
                    }

                    // Seções dos Cargos Apurados
                    itemsIndexed(agregada.cargos) { _, cargo ->
                        CargoResultadosCard(cargo = cargo)
                    }
                }
            }
        }
    }
}

@Composable
private fun CargoResultadosCard(
    cargo: CargoAgregado,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale("pt", "BR")) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabeçalho do Cargo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = cargo.nomeCargo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Votos Válidos: ${numberFormat.format(cargo.votosValidos)} • Total: ${numberFormat.format(cargo.totalVotosCargo)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (cargo.tipoCargo == 0) "Majoritário" else "Proporcional",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Projeção de 2º Turno (apresentado de forma elegante quando aplicável)
            cargo.projecaoSegundoTurno?.let { projecao ->
                Spacer(modifier = Modifier.height(12.dp))
                SegundoTurnoCard(projecao = projecao)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Ranking de Candidatos
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cargo.votosCandidatos.forEachIndexed { index, candidato ->
                    CandidateVoteBar(
                        candidato = candidato,
                        posicao = index + 1,
                        totalVotosValidos = cargo.votosValidos
                    )
                }
            }

            // Estatísticas de Brancos e Nulos
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Brancos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = numberFormat.format(cargo.votosBranco),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nulos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = numberFormat.format(cargo.votosNulos),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (cargo.votosLegenda > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Legenda",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = numberFormat.format(cargo.votosLegenda),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Gráfico de Distribuição por Partido
            val votosPartidos = remember(cargo) {
                val map = mutableMapOf<String, Int>()
                cargo.votosCandidatos.forEach { v ->
                    val partido = v.partidoSigla ?: (v.numero.take(2).let { "Part. $it" })
                    map[partido] = (map[partido] ?: 0) + v.votos
                }
                map
            }

            if (votosPartidos.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                PartyDistributionChart(
                    votosPorPartido = votosPartidos,
                    totalVotosValidos = cargo.votosValidos
                )
            }
        }
    }
}

@Composable
private fun EmptyResultadosState(
    onNavigateToScanner: () -> Unit,
    onCarregarExemploCompleto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nenhum Boletim de Urna Lançado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Escaneie o QR Code impresso no BU da urna eletrônica ou carregue uma eleição geral de exemplo para visualizar gráficos e indicadores.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToScanner,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Escanear QR Code de BU")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onCarregarExemploCompleto,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.HowToVote, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Carregar Eleição Geral Completa (Exemplo TSE)")
        }
    }
}
