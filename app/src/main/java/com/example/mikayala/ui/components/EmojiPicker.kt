package com.example.mikayala.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EmojiItem(val char: String, val name: String, val category: String, val keywords: String = "")

data class CategoryTab(val id: String, val icon: String, val name: String)

/**
 * Catalogue complet (Unicode ≤ 15.0, ~1 870 émojis) avec noms et mots-clés français.
 * Fichier : assets/emoji/emoji_fr.tsv — source emojibase-data (MIT) / CLDR (licence Unicode).
 *
 * Avant : ~420 entrées écrites à la main, dont 43 n'étaient PAS des émojis mais des mots
 * (« Map », « Camping », « Hotel »…) qui s'inséraient tels quels dans le message.
 *
 * Rendu homogène sur les deux téléphones : police Noto Color Emoji embarquée (EmojiCompat,
 * voir MikayalaApplication). Les visuels Apple sont protégés par le droit d'auteur d'Apple :
 * ils ne peuvent pas être intégrés dans l'application.
 */
object EmojiCatalog {
    @Volatile private var cache: List<EmojiItem>? = null

    fun load(context: Context): List<EmojiItem> {
        cache?.let { return it }
        val list = try {
            context.assets.open("emoji/emoji_fr.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                    val p = line.split('\t')
                    if (p.size < 3) null else EmojiItem(
                        char = p[1], name = p[2], category = p[0], keywords = normalize(p[2] + " " + p.getOrElse(3) { "" })
                    )
                }.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        cache = list
        return list
    }

    /** Minuscules sans accents : « cœur » trouvé avec « coeur », « fête » avec « fete ». */
    fun normalize(s: String): String {
        val lower = s.lowercase().replace("œ", "oe").replace("æ", "ae").replace("’", "'")
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
    }

    fun search(all: List<EmojiItem>, query: String): List<EmojiItem> {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.filter { e -> words.all { w -> e.keywords.contains(w) } }
    }
}

private const val RECENTS_KEY = "recent_emojis"
private const val RECENTS_MAX = 32

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mikayala_prefs", Context.MODE_PRIVATE) }

    var allEmojis by remember { mutableStateOf<List<EmojiItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (allEmojis.isEmpty()) allEmojis = withContext(Dispatchers.IO) { EmojiCatalog.load(context) }
    }

    var searchQuery by remember { mutableStateOf("") }
    var recentEmojis by remember {
        mutableStateOf(
            prefs.getString(RECENTS_KEY, "")
                ?.split(",")
                // anciennes entrées « mots » (Map, Camping…) écartées : seuls de vrais émojis restent
                ?.filter { it.isNotBlank() && it.none { c -> c.isLetter() && c.code < 128 } } ?: emptyList()
        )
    }
    var selectedCategory by remember { mutableStateOf(if (recentEmojis.isNotEmpty()) "recents" else "smileys") }

    val categories = remember {
        listOf(
            CategoryTab("recents", "🕒", "Récents"),
            CategoryTab("smileys", "😀", "Émotions"),
            CategoryTab("people", "👋", "Personnes"),
            CategoryTab("animals", "🐱", "Animaux et nature"),
            CategoryTab("food", "🍏", "Nourriture"),
            CategoryTab("activities", "⚽", "Activités"),
            CategoryTab("travel", "🚗", "Voyages"),
            CategoryTab("objects", "💡", "Objets"),
            CategoryTab("symbols", "❤️", "Symboles"),
            CategoryTab("flags", "🏁", "Drapeaux")
        )
    }

    val displayedEmojis = remember(searchQuery, selectedCategory, recentEmojis, allEmojis) {
        when {
            searchQuery.isNotBlank() -> EmojiCatalog.search(allEmojis, searchQuery)
            selectedCategory == "recents" -> recentEmojis.map { EmojiItem(it, "récent", "recents") }
            else -> allEmojis.filter { it.category == selectedCategory }
        }
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(selectedCategory, searchQuery) { gridState.scrollToItem(0) }

    Column(
        modifier = modifier
            .background(CardDarkElevated)
            .padding(top = 8.dp)
    ) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher (cœur, rire, fête…)", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Effacer la recherche", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x1F000000),
                unfocusedContainerColor = Color(0x1F000000),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = VibrantCyan
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .heightIn(min = 46.dp)
        )

        // Catégories : défilement horizontal, cible tactile de 40 dp (10 catégories ne tiennent pas toutes)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(categories, key = { it.id }) { tab ->
                val isSelected = selectedCategory == tab.id && searchQuery.isEmpty()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AccentRose.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable {
                            searchQuery = ""
                            selectedCategory = tab.id
                        }
                        .semantics { contentDescription = tab.name },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = tab.icon, fontSize = 20.sp)
                }
            }
        }

        HorizontalDivider(color = BorderSubtleWhite, thickness = 1.dp)

        val title = if (searchQuery.isNotBlank()) "Résultats" else categories.firstOrNull { it.id == selectedCategory }?.name.orEmpty()
        Text(title, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 6.dp))

        if (displayedEmojis.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        allEmojis.isEmpty() && searchQuery.isBlank() && selectedCategory != "recents" -> "Chargement…"
                        selectedCategory == "recents" && searchQuery.isBlank() -> "Aucun émoji récent"
                        else -> "Aucun émoji trouvé"
                    },
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                // S'adapte à la largeur : ~8 colonnes sur téléphone, davantage sur tablette
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(displayedEmojis, key = { it.category + "_" + it.char }) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onEmojiSelected(emoji.char)
                                val updated = (listOf(emoji.char) + recentEmojis.filter { it != emoji.char }).take(RECENTS_MAX)
                                recentEmojis = updated
                                prefs.edit().putString(RECENTS_KEY, updated.joinToString(",")).apply()
                            }
                            .semantics { contentDescription = emoji.name },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji.char, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}
