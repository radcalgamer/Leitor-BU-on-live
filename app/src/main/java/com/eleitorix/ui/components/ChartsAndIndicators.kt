package com.eleitorix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleitorix.model.SegundoTurnoProjecao
import com.eleitorix.model.VotoCandidato
import com.eleitorix.ui.theme.*
import com.eleitorix.ui.theme.PartyGreen
import com.eleitorix.ui.theme.PartyIndigo
import com.eleitorix.ui.theme.PartyOrange
import com.eleitorix.ui.theme.PartyPink
import com.eleitorix.ui.theme.PartyPurple
import com.eleitorix.ui.theme.PartyTeal
import java.text.NumberFormat
import java.util.Locale

/**
 * Gráfico circular Donut para visualização de Comparecimento e Abstenção
 */
@Composable
fun TurnoutDonutChart(
    aptos: Int,
    comparecimento: Int,
    faltas: Int,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale("pt", "BR")) }
    val taxaComp = if (aptos > 0) (comparecimento.toFloat() / aptos) * 100f else 0f
    val taxaAbs = if (aptos > 0) (faltas.toFloat() / aptos) * 100f else 0f

    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(comparecimento, aptos) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = if (aptos > 0) comparecimento.toFloat() / aptos else 0f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    val corComparecimento = ElectionGreen
    val corAbstencao = MaterialTheme.colorScheme.surfaceVariant
    val corDestaqueAbstencao = ElectionRed

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HowToVote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Comparecimento Eleitoral",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Donut Circular Canvas
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokeWidth = 14.dp.toPx()
                        val arcSize = size.width - strokeWidth

                        // Trilha de Fundo (Abstenção)
                        drawArc(
                            color = corAbstencao,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Arco de Comparecimento animado
                        drawArc(
                            color = corComparecimento,
                            startAngle = -90f,
                            sweepAngle = progressAnim.value * 360f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "%.1f%%".format(taxaComp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = corComparecimento
                        )
                        Text(
                            text = "Presença",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Legenda de Dados
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndicadorItem(
                        cor = corComparecimento,
                        rotulo = "Comparecimento",
                        valor = numberFormat.format(comparecimento),
                        percentual = "%.1f%%".format(taxaComp)
                    )
                    IndicadorItem(
                        cor = corDestaqueAbstencao,
                        rotulo = "Abstenções",
                        valor = numberFormat.format(faltas),
                        percentual = "%.1f%%".format(taxaAbs)
                    )
                    IndicadorItem(
                        cor = MaterialTheme.colorScheme.primary,
                        rotulo = "Eleitores Aptos",
                        valor = numberFormat.format(aptos),
                        percentual = "100%"
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicadorItem(
    cor: Color,
    rotulo: String,
    valor: String,
    percentual: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(cor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = valor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "($percentual)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Barra animada de votação de candidato com nome real, partido e badge do líder
 */
@Composable
fun CandidateVoteBar(
    candidato: VotoCandidato,
    posicao: Int,
    totalVotosValidos: Int,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale("pt", "BR")) }
    val pct = if (totalVotosValidos > 0) (candidato.votos.toFloat() / totalVotosValidos) else 0f

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(pct) {
        animProgress.animateTo(
            targetValue = pct.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    val isLider = posicao == 1
    val barColor = if (isLider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLider) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLider) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLider) {
                        Surface(
                            shape = CircleShape,
                            color = ElectionGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Líder",
                                    tint = ElectionGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${posicao}º",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = candidato.nomeUrna ?: "Candidato ${candidato.numero}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isLider) FontWeight.ExtraBold else FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isLider) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ElectionGold.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "1º LUGAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ElectionGold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nº ${candidato.numero}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            candidato.partidoSigla?.let { sigla ->
                                Text(
                                    text = " • $sigla",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%.2f%%".format(candidato.percentual),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isLider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${numberFormat.format(candidato.votos)} votos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de Progresso
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animProgress.value)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor)
                )
            }
        }
    }
}

/**
 * Card informativo e equilibrado de projeção de 2º Turno (quando o 1º colocado tem <= 50% dos votos válidos no 1º turno)
 */
@Composable
fun SegundoTurnoCard(
    projecao: SegundoTurnoProjecao,
    modifier: Modifier = Modifier
) {
    if (projecao.haveraSegundoTurno && projecao.primeiroColocado != null && projecao.segundoColocado != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ElectionGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Projeção de 2º Turno",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nenhum candidato atingiu mais de 50% dos votos válidos nesta apuração. Disputam o 2º turno:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1º Colocado
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "1º ${projecao.primeiroColocado.nomeUrna}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${projecao.primeiroColocado.partidoSigla ?: ""} • ${"%.1f".format(projecao.percentualPrimeiro)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "VS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElectionGold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // 2º Colocado
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "2º ${projecao.segundoColocado.nomeUrna}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${projecao.segundoColocado.partidoSigla ?: ""} • ${"%.1f".format(projecao.percentualSegundo)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gráfico com distribuição agregada de votos por partido
 */
@Composable
fun PartyDistributionChart(
    votosPorPartido: Map<String, Int>,
    totalVotosValidos: Int,
    modifier: Modifier = Modifier
) {
    if (votosPorPartido.isEmpty() || totalVotosValidos <= 0) return

    val coresPartidos = listOf(
        PartyBlue, PartyRed, PartyGreen, PartyOrange, PartyPurple,
        PartyTeal, PartyAmber, PartyPink, PartyIndigo
    )

    val ordenados = votosPorPartido.toList().sortedByDescending { it.second }.take(8)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Distribuição por Partido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Barra multicor acumulada
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                ordenados.forEachIndexed { index, pair ->
                    val fracao = (pair.second.toFloat() / totalVotosValidos).coerceAtLeast(0.02f)
                    Box(
                        modifier = Modifier
                            .weight(fracao)
                            .height(12.dp)
                            .background(coresPartidos[index % coresPartidos.size])
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legenda em grade de 2 colunas
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ordenados.chunked(2).forEach { parLinha ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        parLinha.forEachIndexed { idxInChunk, pair ->
                            val globalIndex = ordenados.indexOf(pair)
                            val pct = (pair.second.toFloat() / totalVotosValidos) * 100f
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(coresPartidos[globalIndex % coresPartidos.size])
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${pair.first}: ${"%.1f".format(pct)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
