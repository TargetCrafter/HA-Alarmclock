package com.targetcrafter.haalarmclock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/** Minimal manual DI: builds a single-ViewModel factory from a plain lambda, no Hilt needed. */
inline fun <reified VM : ViewModel> appViewModelFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    }
