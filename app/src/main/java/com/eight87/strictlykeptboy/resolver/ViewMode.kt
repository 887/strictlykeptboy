package com.eight87.strictlykeptboy.resolver

sealed interface ViewMode {
    data object Day : ViewMode
    data object Week : ViewMode
    data object Month : ViewMode
    data object Agenda : ViewMode
    data class Todolist(val todolistRef: TodolistRef) : ViewMode
}
