package io.github.vferries.encarte.testing

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.github.vferries.encarte.core.data.EncarteDatabase

fun inMemoryDatabase(): EncarteDatabase =
    Room.inMemoryDatabaseBuilder<EncarteDatabase>(ApplicationProvider.getApplicationContext<Context>()).build()
