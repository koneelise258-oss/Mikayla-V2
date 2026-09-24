package com.example.mikayala.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*

data class EmojiItem(val char: String, val name: String, val category: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mikayala_prefs", Context.MODE_PRIVATE) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("smileys") }
    
    // Recent emojis state
    var recentEmojis by remember { 
        mutableStateOf(
            prefs.getString("recent_emojis", "")
                ?.split(",")
                ?.filter { it.isNotBlank() } ?: emptyList()
        )
    }

    // Default to show recents if available, otherwise smileys
    LaunchedEffect(Unit) {
        if (recentEmojis.isNotEmpty()) {
            selectedCategory = "recents"
        }
    }

    val categories = listOf(
        CategoryTab("recents", "🕒", "Récents"),
        CategoryTab("smileys", "😀", "Visages"),
        CategoryTab("animals", "🐱", "Animaux"),
        CategoryTab("food", "🍏", "Nourriture"),
        CategoryTab("activities", "⚽", "Activités"),
        CategoryTab("travel", "🚗", "Voyages"),
        CategoryTab("objects", "💡", "Objets"),
        CategoryTab("symbols", "❤️", "Symboles"),
        CategoryTab("flags", "🏁", "Drapeaux")
    )

    // Filtered emojis
    val displayedEmojis = remember(searchQuery, selectedCategory, recentEmojis) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            allEmojis.filter { it.name.contains(q) }
        } else if (selectedCategory == "recents") {
            recentEmojis.map { EmojiItem(it, "recent", "recents") }
        } else {
            allEmojis.filter { it.category == selectedCategory }
        }
    }

    Column(
        modifier = modifier
            .background(CardDarkElevated)
            .padding(top = 8.dp)
    ) {
        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un émoji...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Rechercher",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Effacer",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
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
                    .weight(1f)
                    .height(46.dp)
            )
        }

        // Categories Scroll Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            categories.forEach { tab ->
                val isSelected = selectedCategory == tab.id && searchQuery.isEmpty()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            searchQuery = ""
                            selectedCategory = tab.id
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.icon,
                        fontSize = 18.sp,
                        color = if (isSelected) Color.White else TextMuted
                    )
                }
            }
        }

        Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

        // Emoji Grid List
        if (displayedEmojis.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedCategory == "recents") "Aucun émoji récent" else "Aucun émoji trouvé",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayedEmojis, key = { it.char + "_" + it.category }) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onEmojiSelected(emoji.char)
                                
                                // Update recents
                                val recList = recentEmojis.toMutableList()
                                recList.remove(emoji.char)
                                recList.add(0, emoji.char)
                                val updatedList = recList.take(24)
                                recentEmojis = updatedList
                                prefs.edit().putString("recent_emojis", updatedList.joinToString(",")).apply()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji.char,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

data class CategoryTab(val id: String, val icon: String, val name: String)

val allEmojis = listOf(
    // Smileys
    EmojiItem("😀", "smile happy heureux joyeux", "smileys"),
    EmojiItem("😃", "smile happy heureux joyeux", "smileys"),
    EmojiItem("😄", "smile happy heureux joyeux", "smileys"),
    EmojiItem("😁", "smile happy heureux joyeux", "smileys"),
    EmojiItem("😆", "smile happy heureux joyeux", "smileys"),
    EmojiItem("😅", "sweat smile happy heureux", "smileys"),
    EmojiItem("😂", "lol mdr rire joyeux", "smileys"),
    EmojiItem("🤣", "lol mdr rire joyeux", "smileys"),
    EmojiItem("🥲", "larmes smile triste", "smileys"),
    EmojiItem("😊", "smile blush rouge", "smileys"),
    EmojiItem("😇", "ange angel innocent", "smileys"),
    EmojiItem("🙂", "smile ok", "smileys"),
    EmojiItem("🙃", "envers upside down", "smileys"),
    EmojiItem("😉", "clin oeil wink", "smileys"),
    EmojiItem("😌", "soulage ouf", "smileys"),
    EmojiItem("😍", "love amour coeur yeux", "smileys"),
    EmojiItem("🥰", "love amour coeur bisou", "smileys"),
    EmojiItem("😘", "love amour bisou kiss", "smileys"),
    EmojiItem("😗", "bisou kiss", "smileys"),
    EmojiItem("😙", "bisou kiss", "smileys"),
    EmojiItem("😚", "bisou kiss", "smileys"),
    EmojiItem("😋", "miam delicieux miam", "smileys"),
    EmojiItem("😛", "langue tongue", "smileys"),
    EmojiItem("😝", "langue tongue", "smileys"),
    EmojiItem("😜", "langue tongue clin oeil", "smileys"),
    EmojiItem("🤪", "fou crazy", "smileys"),
    EmojiItem("🤨", "interroge sourcil suspicion", "smileys"),
    EmojiItem("🧐", "monocle analyse inspecte", "smileys"),
    EmojiItem("🤓", "intello geek nerd", "smileys"),
    EmojiItem("😎", "cool lunettes soleil", "smileys"),
    EmojiItem("🥸", "deguise masque", "smileys"),
    EmojiItem("🤩", "star etoiles yeux", "smileys"),
    EmojiItem("🥳", "party fete anniversaire", "smileys"),
    EmojiItem("😏", "sourire malin sournois", "smileys"),
    EmojiItem("😒", "blasé ennui unamused", "smileys"),
    EmojiItem("😞", "decu triste deçu", "smileys"),
    EmojiItem("😔", "triste pensif", "smileys"),
    EmojiItem("😟", "inquiet worry", "smileys"),
    EmojiItem("😕", "confus undecided", "smileys"),
    EmojiItem("🙁", "triste frown", "smileys"),
    EmojiItem("☹️", "triste frown", "smileys"),
    EmojiItem("😣", "dur persevere", "smileys"),
    EmojiItem("😖", "confondu tremble", "smileys"),
    EmojiItem("😫", "fatigue tired", "smileys"),
    EmojiItem("😩", "fatigue weary", "smileys"),
    EmojiItem("🥺", "pleure supplie pitié please", "smileys"),
    EmojiItem("😢", "pleure larme cry triste", "smileys"),
    EmojiItem("😭", "pleure larme cry triste", "smileys"),
    EmojiItem("😤", "colere triomphe colère", "smileys"),
    EmojiItem("😠", "colere angry fâche", "smileys"),
    EmojiItem("😡", "colere angry colère fâche rouge", "smileys"),
    EmojiItem("🤬", "colere insulte swear colère", "smileys"),
    EmojiItem("🤯", "tête explose mind blown", "smileys"),
    EmojiItem("😳", "rougit honte rouge", "smileys"),
    EmojiItem("🥵", "chaud hot fievre fever", "smileys"),
    EmojiItem("🥶", "froid cold glace ice", "smileys"),
    EmojiItem("😱", "peur scinde effraye horror screaming", "smileys"),
    EmojiItem("😨", "peur fear", "smileys"),
    EmojiItem("😰", "peur sueur bleue", "smileys"),
    EmojiItem("😥", "soulagement ouf larme", "smileys"),
    EmojiItem("😓", "sueur fatigue", "smileys"),
    EmojiItem("🤗", "calin hug", "smileys"),
    EmojiItem("🤔", "pense thinking", "smileys"),
    EmojiItem("🫣", "regarde cache peeping", "smileys"),
    EmojiItem("🤭", "rire main bouche", "smileys"),
    EmojiItem("🫢", "surprise main bouche", "smileys"),
    EmojiItem("🫡", "salut respect", "smileys"),
    EmojiItem("🤫", "chut secret silence quiet", "smileys"),
    EmojiItem("🫠", "fond melting chaud", "smileys"),
    EmojiItem("🤥", "menteur pinocchio lie", "smileys"),
    EmojiItem("😶", "silence bouche muet", "smileys"),
    EmojiItem("😐", "neutre indifferent", "smileys"),
    EmojiItem("😑", "neutre indifferent", "smileys"),
    EmojiItem("😬", "grimace dent", "smileys"),
    EmojiItem("🙄", "yeux ciel roll eyes", "smileys"),
    EmojiItem("😴", "dort sommeil sleep zzz", "smileys"),
    EmojiItem("🤤", "bave salive hungry miam", "smileys"),
    EmojiItem("😷", "malade masque mask", "smileys"),
    EmojiItem("🤒", "malade fievre thermometre", "smileys"),
    EmojiItem("🤕", "malade blessé pansement", "smileys"),
    EmojiItem("🤢", "malade vomi degoût nausee", "smileys"),
    EmojiItem("🤮", "malade vomi", "smileys"),
    EmojiItem("🤧", "malade rhume atchoum sneeze", "smileys"),
    EmojiItem("🥴", "ivre bourré dizzy dizzy", "smileys"),
    EmojiItem("😵", "mort dizzy dizzy", "smileys"),
    EmojiItem("😵‍💫", "tourbillon dizzy", "smileys"),
    EmojiItem("🤐", "secret silence bouche", "smileys"),
    EmojiItem("🤑", "argent money dollar riche rich", "smileys"),
    EmojiItem("😈", "demon diable devil malin", "smileys"),
    EmojiItem("👿", "demon diable colere", "smileys"),
    EmojiItem("👹", "ogre demon japonais", "smileys"),
    EmojiItem("👺", "tengu masque gobelin", "smileys"),
    EmojiItem("🤡", "clown fete", "smileys"),
    EmojiItem("💩", "caca poop merde", "smileys"),
    EmojiItem("👻", "fantome ghost halloween", "smileys"),
    EmojiItem("💀", "mort squelette skull halloween", "smileys"),
    EmojiItem("☠️", "mort squelette pirate danger", "smileys"),
    EmojiItem("👽", "alien ovni espace", "smileys"),
    EmojiItem("👾", "alien robot monstre jeu video", "smileys"),
    EmojiItem("🤖", "robot technologie", "smileys"),
    EmojiItem("🎃", "citrouille halloween fete", "smileys"),
    EmojiItem("😺", "chat cat happy", "smileys"),
    EmojiItem("😸", "chat cat happy", "smileys"),
    EmojiItem("😹", "chat cat mdr lol", "smileys"),
    EmojiItem("😻", "chat cat love coeur yeux", "smileys"),
    EmojiItem("😼", "chat cat malin", "smileys"),
    EmojiItem("😽", "chat cat bisou", "smileys"),
    EmojiItem("🙀", "chat cat surprise", "smileys"),
    EmojiItem("😿", "chat cat triste larme", "smileys"),
    EmojiItem("😾", "chat cat fâche colere", "smileys"),

    // Hands
    EmojiItem("👋", "coucou salut bye wave main hello", "smileys"),
    EmojiItem("🤚", "main levée backhand raised hand", "smileys"),
    EmojiItem("🖐️", "main ouverte hand splayed", "smileys"),
    EmojiItem("✋", "main levée stop raised hand", "smileys"),
    EmojiItem("👌", "ok super daccord hand okay", "smileys"),
    EmojiItem("🤌", "pinçon geste italien", "smileys"),
    EmojiItem("🤏", "un peu petit pinch", "smileys"),
    EmojiItem("✌️", "peace victoire victory salut", "smileys"),
    EmojiItem("🤞", "croise doigts chance fingers crossed", "smileys"),
    EmojiItem("🫰", "coeur doigt love korean heart", "smileys"),
    EmojiItem("🤟", "love je t'aime love sign", "smileys"),
    EmojiItem("🤘", "rock metal cornes", "smileys"),
    EmojiItem("🤙", "appelle téléphone call me", "smileys"),
    EmojiItem("👈", "gauche point left", "smileys"),
    EmojiItem("👉", "droite point right", "smileys"),
    EmojiItem("👆", "haut point up", "smileys"),
    EmojiItem("🖕", "doigt d'honneur middle finger", "smileys"),
    EmojiItem("👇", "bas point down", "smileys"),
    EmojiItem("☝️", "haut index point up", "smileys"),
    EmojiItem("👍", "super cool yes ok pouce raised hand thumb up daccord", "smileys"),
    EmojiItem("👎", "nul pas cool no pouce down thumb down", "smileys"),
    EmojiItem("✊", "poing force power fist", "smileys"),
    EmojiItem("👊", "poing punch fist", "smileys"),
    EmojiItem("🤛", "poing gauche", "smileys"),
    EmojiItem("🤜", "poing droit", "smileys"),
    EmojiItem("👏", "bravo applaudit clap mains", "smileys"),
    EmojiItem("🙌", "celebration mains en l'air raised hands", "smileys"),
    EmojiItem("👐", "ouvert open hands", "smileys"),
    EmojiItem("🤲", "prière mains jointes palms up", "smileys"),
    EmojiItem("🤝", "alliance accord poignée mains handshake", "smileys"),
    EmojiItem("🙏", "prière merci svp please pray mains jointes", "smileys"),
    EmojiItem("✍️", "ecrit stylo main write", "smileys"),
    EmojiItem("💅", "vernis ongles manicure", "smileys"),
    EmojiItem("🤳", "selfie photo main", "smileys"),
    EmojiItem("💪", "force muscle biceps sport", "smileys"),
    EmojiItem("🦵", "jambe leg", "smileys"),
    EmojiItem("🦶", "pied foot", "smileys"),
    EmojiItem("👂", "oreille ear", "smileys"),
    EmojiItem("👃", "nez nose", "smileys"),
    EmojiItem("🧠", "cerveau brain intelligence", "smileys"),
    EmojiItem("🦷", "dent tooth", "smileys"),
    EmojiItem("👀", "yeux eyes regarde look", "smileys"),
    EmojiItem("👁️", "oeil eye regarde look", "smileys"),
    EmojiItem("👅", "langue tongue", "smileys"),
    EmojiItem("👄", "bouche mouth", "smileys"),
    EmojiItem("💋", "bisou kiss levres rouge", "smileys"),

    // Animals & Nature
    EmojiItem("🐶", "chien dog animal", "animals"),
    EmojiItem("🐱", "chat cat animal", "animals"),
    EmojiItem("🐭", "souris mouse animal", "animals"),
    EmojiItem("🐹", "hamster animal", "animals"),
    EmojiItem("🐰", "lapin rabbit animal", "animals"),
    EmojiItem("Fox", "renard fox animal", "animals"),
    EmojiItem("🐻", "ours bear animal", "animals"),
    EmojiItem("🐼", "panda animal", "animals"),
    EmojiItem("🐨", "koala animal", "animals"),
    EmojiItem("🐯", "tigre tiger animal", "animals"),
    EmojiItem("🦁", "lion animal", "animals"),
    EmojiItem("🐮", "vache cow animal", "animals"),
    EmojiItem("🐷", "cochon pig animal", "animals"),
    EmojiItem("🐸", "grenouille frog animal", "animals"),
    EmojiItem("🐵", "singe monkey animal", "animals"),
    EmojiItem("🙈", "singe cache yeux", "animals"),
    EmojiItem("🙉", "singe cache oreilles", "animals"),
    EmojiItem("🙊", "singe cache bouche", "animals"),
    EmojiItem("🐧", "pingouin penguin", "animals"),
    EmojiItem("Bird", "oiseau bird", "animals"),
    EmojiItem("🐦", "oiseau bird", "animals"),
    EmojiItem("🦆", "canard duck", "animals"),
    EmojiItem("🦅", "aigle eagle", "animals"),
    EmojiItem("🦉", "hibou owl", "animals"),
    EmojiItem("🐝", "abeille bee miel honey", "animals"),
    EmojiItem("🦋", "papillon butterfly", "animals"),
    EmojiItem("🐌", "escargot snail", "animals"),
    EmojiItem("🐞", "coccinelle ladybug", "animals"),
    EmojiItem("🕷️", "araignee spider halloween", "animals"),
    EmojiItem("🐢", "tortue turtle", "animals"),
    EmojiItem("🐍", "serpent snake", "animals"),
    EmojiItem("🐙", "poulpe octopus", "animals"),
    EmojiItem("🐠", "poisson tropical fish", "animals"),
    EmojiItem("🐟", "poisson fish", "animals"),
    EmojiItem("🐬", "dauphin dolphin", "animals"),
    EmojiItem("Whale", "baleine whale", "animals"),
    EmojiItem("🐳", "baleine whale", "animals"),
    EmojiItem("🦈", "requin shark", "animals"),
    EmojiItem("🐊", "crocodile", "animals"),
    EmojiItem("🐆", "leopard leopard", "animals"),
    EmojiItem("🦍", "gorille gorilla", "animals"),
    EmojiItem("Elephant", "elephant elephant", "animals"),
    EmojiItem("🐘", "elephant elephant", "animals"),
    EmojiItem("🐫", "chameau camel", "animals"),
    EmojiItem("Giraffe", "girafe giraffe", "animals"),
    EmojiItem("🦒", "girafe giraffe", "animals"),
    EmojiItem("🐏", "belier ram", "animals"),
    EmojiItem("Sheep", "mouton sheep", "animals"),
    EmojiItem("🐑", "mouton sheep", "animals"),
    EmojiItem("🐐", "chevre goat", "animals"),
    EmojiItem("Deer", "cerf deer", "animals"),
    EmojiItem("🦌", "cerf deer", "animals"),
    EmojiItem("🐈", "chat cat", "animals"),
    EmojiItem("🐓", "coq rooster", "animals"),
    EmojiItem("🦃", "dinde turkey", "animals"),
    EmojiItem("🕊️", "colombe dove paix peace", "animals"),
    EmojiItem("🐇", "lapin rabbit", "animals"),
    EmojiItem("🐾", "pattes traces paw prints", "animals"),
    EmojiItem("🐉", "dragon", "animals"),
    EmojiItem("🌵", "cactus desert", "animals"),
    EmojiItem("🎄", "sapin noel christmas tree", "animals"),
    EmojiItem("🌲", "sapin pine tree", "animals"),
    EmojiItem("🌳", "arbre tree", "animals"),
    EmojiItem("🌴", "palmier palm tree", "animals"),
    EmojiItem("🌱", "pousse seedling plante plant", "animals"),
    EmojiItem("🍀", "trefle 4 feuilles clover chance", "animals"),
    EmojiItem("🍁", "feuille erable maple leaf canada", "animals"),
    EmojiItem("🍂", "feuille morte fallen leaf automne", "animals"),
    EmojiItem("🍄", "champignon mushroom mario", "animals"),
    EmojiItem("Shell", "coquillage seashell ocean", "animals"),
    EmojiItem("🐚", "coquillage seashell ocean", "animals"),
    EmojiItem("🌹", "rose rouge fleur flower love", "animals"),
    EmojiItem("🥀", "rose flétrie wilted flower", "animals"),
    EmojiItem("🌸", "cerisier fleur blossom flower japan cherry", "animals"),
    EmojiItem("🌻", "tournesol sunflower flower", "animals"),
    EmojiItem("☀️", "soleil sun chaud hot", "animals"),
    EmojiItem("🌙", "croissant lune crescent moon", "animals"),
    EmojiItem("✨", "etincelles sparkles magie magic", "animals"),
    EmojiItem("🔥", "feu fire chaud hot sexy", "animals"),
    EmojiItem("❄️", "neige flocon snowflake cold froid winter", "animals"),

    // Food & Drink
    EmojiItem("🍏", "pomme verte apple green", "food"),
    EmojiItem("🍎", "pomme rouge apple red", "food"),
    EmojiItem("🍊", "orange mandarine", "food"),
    EmojiItem("🍋", "citron lemon", "food"),
    EmojiItem("🍌", "banane banana fruit jaune sex", "food"),
    EmojiItem("🍉", "pasteque watermelon fruit", "food"),
    EmojiItem("🍇", "raisin grapes fruit", "food"),
    EmojiItem("🍓", "fraise strawberry fruit", "food"),
    EmojiItem("🍒", "cerise cherry fruit", "food"),
    EmojiItem("🍑", "peche peach fruit fesses ass sex", "food"),
    EmojiItem("🍍", "ananas pineapple", "food"),
    EmojiItem("🥥", "noix coco coconut", "food"),
    EmojiItem("Avocado", "avocat avocado", "food"),
    EmojiItem("🥑", "avocat avocado", "food"),
    EmojiItem("Eggplant", "aubergine eggplant sex penis", "food"),
    EmojiItem("🍆", "aubergine eggplant sex penis", "food"),
    EmojiItem("🌶️", "piment hot pepper rouge sexy épice", "food"),
    EmojiItem("🌽", "mais corn", "food"),
    EmojiItem("Carrot", "carotte carrot", "food"),
    EmojiItem("🥕", "carotte carrot", "food"),
    EmojiItem("Croissant", "croissant boulangerie france french", "food"),
    EmojiItem("🥐", "croissant boulangerie france french", "food"),
    EmojiItem("🧀", "fromage cheese jaune", "food"),
    EmojiItem("🍳", "oeuf plat poele frying egg cooking", "food"),
    EmojiItem("🥞", "pancakes crêpes", "food"),
    EmojiItem("Hamburger", "burger hamburger fastfood", "food"),
    EmojiItem("🍔", "burger hamburger fastfood", "food"),
    EmojiItem("🍟", "frites french fries fastfood", "food"),
    EmojiItem("🍕", "pizza fastfood", "food"),
    EmojiItem("Sandwich", "sandwich", "food"),
    EmojiItem("🥪", "sandwich", "food"),
    EmojiItem("Taco", "taco mexique mexican", "food"),
    EmojiItem("🌮", "taco mexique mexican", "food"),
    EmojiItem("Sushi", "sushi japon japanese raw fish", "food"),
    EmojiItem("🍣", "sushi japon japanese raw fish", "food"),
    EmojiItem("🍿", "popcorn pop corn cinema", "food"),
    EmojiItem("IceCream", "glace soft ice cream", "food"),
    EmojiItem("🍦", "glace soft ice cream", "food"),
    EmojiItem("🍩", "donut beignet donut", "food"),
    EmojiItem("🍪", "cookie biscuit sucré chocolat", "food"),
    EmojiItem("🎂", "gateau fete anniversaire birthday cake", "food"),
    EmojiItem("Cupcake", "cupcake gateau", "food"),
    EmojiItem("🧁", "cupcake gateau", "food"),
    EmojiItem("🍫", "chocolat chocolate", "food"),
    EmojiItem("Candy", "bonbon candy", "food"),
    EmojiItem("🍬", "bonbon candy", "food"),
    EmojiItem("🍭", "sucette lollipop candy", "food"),
    EmojiItem("Honey", "miel honey pot bee", "food"),
    EmojiItem("🍯", "miel honey pot bee", "food"),
    EmojiItem("☕️", "cafe coffee tea chaud hot", "food"),
    EmojiItem("🍷", "vin rouge wine alcool glass", "food"),
    EmojiItem("Cocktail", "cocktail alcool martini", "food"),
    EmojiItem("🍸", "cocktail alcool martini", "food"),
    EmojiItem("🍹", "cocktail tropical juice alcool fruit", "food"),
    EmojiItem("🍺", "biere beer alcool glass chopes", "food"),
    EmojiItem("🍻", "bieres beer alcool toast cheers", "food"),
    EmojiItem("🥂", "champagne toasts cheers celebration glass", "food"),
    EmojiItem("Soda", "soda cup cola fastfood boisson", "food"),
    EmojiItem("🥤", "soda cup cola fastfood boisson", "food"),

    // Activities & Sports
    EmojiItem("⚽️", "football ballon foot soccer sport", "activities"),
    EmojiItem("🏀", "basketball ballon basket sport", "activities"),
    EmojiItem("🏈", "football americain rugby sport", "activities"),
    EmojiItem("🎾", "tennis balle sport", "activities"),
    EmojiItem("🎱", "billard 8ball sport", "activities"),
    EmojiItem("🏓", "pingpong table tennis sport", "activities"),
    EmojiItem("🥊", "boxe gant sport boxing", "activities"),
    EmojiItem("🛹", "skateboard skate board sport", "activities"),
    EmojiItem("🧘‍♀️", "yoga meditation zen spirit", "activities"),
    EmojiItem("Swimming", "natation piscine swim sport", "activities"),
    EmojiItem("🏊‍♀️", "natation piscine swim sport", "activities"),
    EmojiItem("🏆", "coupe trophée win victory gold premium", "activities"),
    EmojiItem("🥇", "medaille or gold medal win first", "activities"),
    EmojiItem("Ticket", "ticket billet concert spectacle cinema", "activities"),
    EmojiItem("🎟️", "ticket billet concert spectacle cinema", "activities"),
    EmojiItem("🎨", "peinture palette dessin art paint", "activities"),
    EmojiItem("🎬", "clapper cinema movie film", "activities"),
    EmojiItem("🎤", "micro microphone chante karaoke song sing music", "activities"),
    EmojiItem("🎧", "casque audio headphones music", "activities"),
    EmojiItem("🎹", "piano clavier keyboard music", "activities"),
    EmojiItem("🎸", "guitare guitar rock music", "activities"),
    EmojiItem("Dice", "de dice jeux games casino board game", "activities"),
    EmojiItem("🎲", "de dice jeux games casino board game", "activities"),
    EmojiItem("🎯", "cible flechette dart bullseye hit", "activities"),
    EmojiItem("🎮", "console manette video game controller gamer play", "activities"),
    EmojiItem("Puzzle", "puzzle piece game", "activities"),
    EmojiItem("🧩", "puzzle piece game", "activities"),

    // Travel & Places
    EmojiItem("🚗", "voiture car travel auto", "travel"),
    EmojiItem("🚙", "voiture suv auto blue car", "travel"),
    EmojiItem("🏍️", "moto motorbike speed sport", "travel"),
    EmojiItem("🚲", "velo bike bicyclette sport travel", "travel"),
    EmojiItem("Road", "route motorway highway", "travel"),
    EmojiItem("🛣️", "route motorway highway", "travel"),
    EmojiItem("Map", "carte map world travel guide", "travel"),
    EmojiItem("🗺️", "carte map world travel guide", "travel"),
    EmojiItem("🏔️", "montagne enneigée snow peak alps climb", "travel"),
    EmojiItem("Camping", "camping tent forest outdoor", "travel"),
    EmojiItem("🏕️", "camping tent forest outdoor", "travel"),
    EmojiItem("🏖️", "plage beach sea ocean summer sun island", "travel"),
    EmojiItem("Island", "ile desert island palm ocean beach summer", "travel"),
    EmojiItem("🏝️", "ile desert island palm ocean beach summer", "travel"),
    EmojiItem("House", "maison house home building", "travel"),
    EmojiItem("🏠", "maison house home building", "travel"),
    EmojiItem("Hotel", "hotel travel room sleep", "travel"),
    EmojiItem("🏨", "hotel travel room sleep", "travel"),
    EmojiItem("🏩", "love hotel couple room sex", "travel"),
    EmojiItem("Eiffel", "tokyo tower paris eiffel red tower", "travel"),
    EmojiItem("Tower", "tokyo tower paris eiffel red tower", "travel"),
    EmojiItem("🗼", "tokyo tower paris eiffel red tower", "travel"),
    EmojiItem("Liberty", "statue liberte ny liberty usa", "travel"),
    EmojiItem("🗽", "statue liberte ny liberty usa", "travel"),
    EmojiItem("Wedding", "mariage wedding chapel church love ring couple", "travel"),
    EmojiItem("💒", "mariage wedding chapel church love ring couple", "travel"),
    EmojiItem("Sunset", "sunset coucher soleil orange sky landscape", "travel"),
    EmojiItem("🌅", "sunset coucher soleil orange sky landscape", "travel"),
    EmojiItem("City", "ville city skyline skyscrapers night", "travel"),
    EmojiItem("🌇", "ville city skyline skyscrapers sunset", "travel"),
    EmojiItem("🌃", "ville nuit city skyline night stars", "travel"),

    // Objects
    EmojiItem("📱", "telephone phone smartphone screen mobile", "objects"),
    EmojiItem("💻", "ordinateur computer laptop screen tech", "objects"),
    EmojiItem("📸", "appareil photo flash camera photography flash memory", "objects"),
    EmojiItem("🎥", "camera cinema movie film projector recorder", "objects"),
    EmojiItem("📺", "television tv screen show watch video", "objects"),
    EmojiItem("🎙️", "micro microphone studio recording music song podcast", "objects"),
    EmojiItem("⏰", "reveil alarm clock time heure", "objects"),
    EmojiItem("⏳", "sablier hourglass time flow waiting delay", "objects"),
    EmojiItem("🔋", "batterie battery energy power charge", "objects"),
    EmojiItem("🔌", "prise electrique plug power energy wire", "objects"),
    EmojiItem("💡", "ampoule light bulb idea eureka lumiere", "objects"),
    EmojiItem("Flashlight", "lampe torche flashlight beam dark shadow light", "objects"),
    EmojiItem("🔦", "lampe torche flashlight beam dark shadow light", "objects"),
    EmojiItem("Candle", "bougie candle fire flame light wax warm romantic", "objects"),
    EmojiItem("🕯️", "bougie candle fire flame light wax warm romantic", "objects"),
    EmojiItem("💸", "argent dollar money fly wings bank riches riche", "objects"),
    EmojiItem("💵", "argent dollar money cash bank paper note bill", "objects"),
    EmojiItem("💰", "argent sac money bag cash riche rich luxury", "objects"),
    EmojiItem("CreditCard", "carte credit bank card money payment business", "objects"),
    EmojiItem("💳", "carte credit bank card money payment business", "objects"),
    EmojiItem("💎", "diamant diamond jewel crystal shiny luxury love ring proposal", "objects"),
    EmojiItem("Key", "cle key lock open secret door clue entry", "objects"),
    EmojiItem("🔑", "cle key lock open secret door clue entry", "objects"),
    EmojiItem("Book", "livre book read study", "objects"),
    EmojiItem("✉️", "lettre enveloppe envelope letter mail paper write", "objects"),
    EmojiItem("📝", "note memo pencil paper write note pad", "objects"),
    EmojiItem("📅", "calendrier calendar date schedule planner day year", "objects"),
    EmojiItem("🗑️", "poubelle trash can rubbish waste delete bin", "objects"),

    // Symbols & Hearts
    EmojiItem("💘", "coeur fleche heart arrow cupid love amour valentin saint", "symbols"),
    EmojiItem("💝", "coeur ruban heart ribbon gift cadeau love amour present", "symbols"),
    EmojiItem("💖", "coeur brillant sparkling heart love amour sparks shine", "symbols"),
    EmojiItem("💗", "coeur grandissant growing heart love amour pink rose", "symbols"),
    EmojiItem("💓", "coeur battant beating heart love amour pink rose pulse vibration", "symbols"),
    EmojiItem("💞", "coeurs tournants revolving hearts love amour couple pink rose", "symbols"),
    EmojiItem("💕", "deux coeurs two hearts love amour pink rose couple side", "symbols"),
    EmojiItem("❤️", "coeur rouge red heart love amour hot romance passion saint valentin", "symbols"),
    EmojiItem("🧡", "coeur orange heart love passion warm autumn", "symbols"),
    EmojiItem("💛", "coeur jaune heart love gold sunshine friendship ami", "symbols"),
    EmojiItem("💚", "coeur vert heart love green nature eco friendship", "symbols"),
    EmojiItem("💙", "coeur bleu heart love blue ocean water trust", "symbols"),
    EmojiItem("💜", "coeur violet heart love purple violet luxury noble", "symbols"),
    EmojiItem("🖤", "coeur noir heart love black dark gothic emo shadow", "symbols"),
    EmojiItem("🤍", "coeur blanc heart love white light pure peace clean", "symbols"),
    EmojiItem("💯", "100 pour cent grade exam perfect success hit win red", "symbols"),
    EmojiItem("💬", "bulle texte speech bubble chat conversation message talk sms", "symbols"),
    EmojiItem("💭", "bulle pensee thought bubble think dream dream sleeping", "symbols"),
    EmojiItem("💤", "sommeil sleep zzz night tired snoring", "symbols"),
    EmojiItem("⚠️", "danger warning attention yellow hazard alert", "symbols"),
    EmojiItem("🚫", "interdit prohibited red circle slash block stop", "symbols"),
    EmojiItem("🔞", "interdit mineurs 18 red circle age limit adult explicit", "symbols"),
    EmojiItem("🎵", "note musique music note song melody sound tone", "symbols"),
    EmojiItem("🎶", "notes musique music notes melody sound band song", "symbols"),
    EmojiItem("➕", "plus add math sum sign cross symbol", "symbols"),
    EmojiItem("♾️", "infini infinity forever eternity loop love limit", "symbols"),
    EmojiItem("🔴", "rond rouge red circle button badge dot ball", "symbols"),
    EmojiItem("🔵", "rond bleu blue circle button badge dot ball", "symbols"),

    // Flags
    EmojiItem("🏁", "drapeau damier checkered flag race finish speed win auto", "flags"),
    EmojiItem("🚩", "drapeau rouge red flag alert warning golf mark triangle", "flags"),
    EmojiItem("🏳️‍🌈", "drapeau arc en ciel rainbow flag lgtb pride fierte love", "flags"),
    EmojiItem("🏴‍☠️", "drapeau pirate pirate flag skull bones danger crossbones", "flags"),
    EmojiItem("🇫🇷", "drapeau france french flag paris europe country nation", "flags"),
    EmojiItem("🇬🇧", "drapeau angleterre uk flag london british english country", "flags"),
    EmojiItem("🇺🇸", "drapeau usa american flag america country nation us english", "flags"),
    EmojiItem("🇪🇸", "drapeau espagne spanish flag madrid country nation europe", "flags"),
    EmojiItem("🇩🇪", "drapeau allemagne german flag berlin country nation europe", "flags"),
    EmojiItem("🇮🇹", "drapeau italie italian flag rome country nation europe", "flags"),
    EmojiItem("🇨🇦", "drapeau canada canadian flag maple leaf country nation", "flags"),
    EmojiItem("🇯🇵", "drapeau japon japanese flag tokyo country nation red sun", "flags")
)
