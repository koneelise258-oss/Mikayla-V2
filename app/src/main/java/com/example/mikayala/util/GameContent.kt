package com.example.mikayala.util

/**
 * Contenus des jeux du chat. Avant : un seul texte possible par jeu (même carte à gratter, même bon
 * d'amour, même « Tu préfères » à chaque fois) et un quiz dont la « bonne réponse » était inventée.
 * Les tirages sont faits sur le téléphone qui lance le jeu ; le résultat est ensuite partagé tel quel.
 */
object GameContent {

    val scratchPrizes = listOf(
        "Massage du dos de 20 minutes 💆",
        "Petit-déjeuner au lit 🥐",
        "Soirée film : tu choisis le film 🎬",
        "Un dîner préparé rien que pour toi 🍽️",
        "Une balade main dans la main 🌅",
        "Un câlin de 5 minutes sans téléphone 🫂",
        "Je fais une corvée à ta place 🧹",
        "Une playlist faite pour toi 🎶",
        "Un mot doux caché quelque part 💌",
        "Une soirée jeux de société 🎲",
        "Un baiser surprise quand tu t'y attends le moins 💋",
        "Tu décides du programme de samedi 📅"
    )

    val coupons = listOf(
        "Joker câlin à utiliser quand tu veux 🫂",
        "Bon pour un massage des pieds 🦶",
        "Bon pour gagner une dispute (une seule fois) 😇",
        "Bon pour un repas de ton choix 🍕",
        "Bon pour une grasse matinée tranquille 😴",
        "Bon pour une sortie que tu choisis 🎟️",
        "Bon pour une soirée sans écrans 📵",
        "Bon pour un fou rire garanti 😂"
    )

    val truthOrDare = listOf(
        "✨ Vérité : quel a été le moment exact où tu as su que tu tombais amoureux(se) ?",
        "✨ Vérité : quel souvenir de nous deux te fait toujours sourire ?",
        "✨ Vérité : qu'est-ce que tu n'as jamais osé me dire ?",
        "✨ Vérité : quel petit geste de ma part te touche le plus ?",
        "✨ Vérité : où aimerais-tu qu'on parte ensemble ?",
        "✨ Vérité : qu'est-ce que tu as pensé de moi la toute première fois ?",
        "🔥 Action : envoie-moi un vocal où tu chantes notre chanson.",
        "🔥 Action : écris-moi trois choses que tu aimes chez moi.",
        "🔥 Action : envoie une photo de ce que tu vois en ce moment.",
        "🔥 Action : propose-moi un rendez-vous pour cette semaine.",
        "🔥 Action : fais-moi un compliment que tu ne m'as jamais fait.",
        "🔥 Action : imite ma façon de parler dans un vocal."
    )

    /** Paires « A|B » au format du jeu « Tu préfères » du chat. */
    val dilemmas = listOf(
        "Un voyage surprise 🧳|Une semaine de vacances à la maison 🏡",
        "Un dîner aux chandelles 🕯️|Un pique-nique au soleil 🧺",
        "Regarder le lever du soleil 🌅|Regarder les étoiles 🌌",
        "Danser toute la nuit 💃|Une soirée film sous la couette 🍿",
        "La mer 🌊|La montagne 🏔️",
        "Cuisiner ensemble 🍳|Se faire livrer 🛵",
        "Recevoir une lettre d'amour 💌|Recevoir un vocal d'amour 🎙️",
        "Un week-end sans téléphone 📵|Un week-end à tout photographier 📸",
        "Revivre notre premier rendez-vous 💞|Découvrir un endroit nouveau ensemble 🗺️",
        "Un câlin de 10 minutes 🫂|Dix baisers 💋"
    )
}
