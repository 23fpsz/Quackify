package com.quackify.compose.util

/**
 * Translates Spotify Browse category titles for Indonesian display.
 * Original titles and Spotify URIs must remain unchanged in the data layer.
 */
fun localizeBrowseCategory(title: String): String = when (title.trim()) {
    "Music" -> "Musik"
    "Podcasts" -> "Podcast"
    "Audiobooks" -> "Buku Audio"
    "Live Events" -> "Acara Langsung"
    "Made For You" -> "Dibuat Untukmu"
    "New Releases" -> "Rilisan Baru"
    "Charts" -> "Tangga Lagu"
    "Podcast Charts" -> "Tangga Lagu Podcast"
    "Podcast New Releases" -> "Rilisan Podcast Baru"
    "Video Podcast" -> "Podcast Video"
    "In the car" -> "Di Mobil"
    "Kids & Family" -> "Anak & Keluarga"
    "Discover" -> "Jelajahi"
    "Fresh Finds" -> "Temuan Musik Baru"
    "Dance/Electronic" -> "Dance/Elektronik"
    "Mood" -> "Suasana"
    "Sleep" -> "Tidur"
    "At Home" -> "Di Rumah"
    "Chill" -> "Santai"
    "Malay pop" -> "Pop Melayu"
    "Party" -> "Pesta"
    "Workout Music" -> "Musik Olahraga"
    "Love" -> "Cinta"
    "Hometown" -> "Kota Asal"
    "Decades" -> "Lintas Dekade"
    "Trending" -> "Sedang Tren"
    "Folk & Acoustic" -> "Folk & Akustik"
    "Focus" -> "Fokus"
    "Gaming" -> "Gim"
    "TV & Movies" -> "TV & Film"
    "Cooking & Dining" -> "Memasak & Kuliner"
    "Alternative" -> "Alternatif"
    "Travel" -> "Wisata"
    "Classical" -> "Klasik"
    "Songwriters" -> "Penulis Lagu"
    "Nature & Noise" -> "Alam & Suara"
    "Wellness" -> "Kebugaran"
    "Tastemakers" -> "Kurator Musik"
    "Caribbean" -> "Karibia"
    else -> title
}
