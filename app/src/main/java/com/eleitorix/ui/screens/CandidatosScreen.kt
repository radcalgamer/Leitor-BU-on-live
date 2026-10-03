package com.eleitorix.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleitorix.data.db.CandidatoEntity
import com.eleitorix.data.repository.CandidatoRepository
import com.eleitorix.model.CargoNomes
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CandidatosScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { CandidatoRepository(context) }
    val scope = rememberCoroutineScope()

    val totalCandidatos by repository.getCountFlow().collectAsState(initial = 0)
    val anosDisponiveis by repository.getAnosDisponiveisFlow().collectAsState(initial = emptyList())

    var anoSelecionado by remember { mutableStateOf(2026) }
    var cargoFiltro by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var candidatosExibidos by remember { mutableStateOf<List<CandidatoEntity>>(emptyList()) }
    var isImportando by remember { mutableStateOf(false) }

    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")) }

    // Atualiza listagem quando filtros mudam
    LaunchedEffect(anoSelecionado, cargoFiltro, searchQuery, totalCandidatos) {
        candidatosExibidos = repository.filtrarCandidatos(
            ano = anoSelecionado,
            uf = "",
            cargo = cargoFiltro,
            query = searchQuery
        )
    }

    // Seletor de arquivo CSV via SAF (Storage Access Framework)
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImportando = true
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        // Detecta se o arquivo é UTF-8 válido estrito ou ISO-8859-1 (padrão oficial do TSE)
                        val charsetUsado = try {
                            val decoder = Charsets.UTF_8.newDecoder()
                            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
                            Charsets.UTF_8
                        } catch (_: java.nio.charset.CharacterCodingException) {
                            Charsets.ISO_8859_1
                        }

                        val result = repository.importarCsv(bytes.inputStream(), charsetUsado)
                        if (result.isSuccess) {
                            val count = result.getOrNull() ?: 0
                            val nomeCharset = if (charsetUsado == Charsets.UTF_8) "UTF-8" else "ISO-8859-1"
                            Toast.makeText(context, "$count candidatos importados com sucesso ($nomeCharset)!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Erro ao importar: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Falha na leitura do arquivo: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isImportando = false
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Painel de Importação & Automação de Anos
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Base de Candidatos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Importe CSVs do TSE ou preencha anos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Estatísticas da base
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Total Cadastrado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = numberFormat.format(totalCandidatos),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Anos Disponíveis",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (anosDisponiveis.isEmpty()) "0" else anosDisponiveis.joinToString(", "),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Botão Importar CSV
                    Button(
                        onClick = { csvPickerLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isImportando
                    ) {
                        if (isImportando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Processando CSV...")
                        } else {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Importar Arquivo CSV (TSE)")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Seção de Preenchimento Automático por Ano
                    Text(
                        text = "Preencher Anos Automaticamente:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2026, 2024, 2022, 2020).forEach { ano ->
                            val jaTem = anosDisponiveis.contains(ano)
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isImportando = true
                                        val inseridos = repository.preencherAno(ano)
                                        anoSelecionado = ano
                                        isImportando = false
                                        Toast.makeText(context, "Ano $ano populado com $inseridos candidatos!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$ano",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (jaTem) "✓ Pronto" else "+ Carregar",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (jaTem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Barra de Busca e Filtros
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Pesquisar candidato por nome ou número...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Filtro por Ano
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val anosLista = if (anosDisponiveis.isNotEmpty()) anosDisponiveis else listOf(2026, 2024, 2022, 2020)
                    anosLista.forEach { ano ->
                        FilterChip(
                            selected = anoSelecionado == ano,
                            onClick = { anoSelecionado = ano },
                            label = { Text("Eleição $ano") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                // Filtro por Cargo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = cargoFiltro == 0,
                        onClick = { cargoFiltro = 0 },
                        label = { Text("Todos os Cargos") }
                    )
                    listOf(
                        CargoNomes.PRESIDENTE,
                        CargoNomes.GOVERNADOR,
                        CargoNomes.SENADOR,
                        CargoNomes.DEPUTADO_FEDERAL,
                        CargoNomes.DEPUTADO_ESTADUAL,
                        CargoNomes.PREFEITO,
                        CargoNomes.VEREADOR
                    ).forEach { codCargo ->
                        FilterChip(
                            selected = cargoFiltro == codCargo,
                            onClick = { cargoFiltro = if (cargoFiltro == codCargo) 0 else codCargo },
                            label = { Text(CargoNomes.getNomeCargo(codCargo)) }
                        )
                    }
                }
            }
        }

        // Lista de Candidatos Encontrados
        item {
            Text(
                text = "${candidatosExibidos.size} candidatos listados:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(candidatosExibidos, key = { it.id }) { cand ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = cand.numero,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cand.nomeUrna,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            if (cand.partidoSigla.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = cand.partidoSigla,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${CargoNomes.getNomeCargo(cand.cargo)} • ${cand.uf}${if (cand.nomeUe.isNotBlank() && cand.nomeUe != cand.uf) " - ${cand.nomeUe}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}