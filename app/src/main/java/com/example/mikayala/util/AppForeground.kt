package com.example.mikayala.util

/**
 * Application visible à l'écran (entre ON_START et ON_STOP de l'activité).
 * Une composition Compose reste active en arrière-plan : « écran de conversation composé »
 * ne veut donc pas dire « conversation vue ». Sert aux notifications et aux accusés de lecture.
 */
object AppForeground {
    @Volatile
    var isResumed: Boolean = false
}
