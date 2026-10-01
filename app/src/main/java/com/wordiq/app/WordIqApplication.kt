package com.wordiq.app

import android.app.Application
import com.wordiq.app.data.UserPreferencesRepository
import com.wordiq.app.data.PracticeRepository
import com.wordiq.app.data.DataPortabilityRepository
import com.wordiq.app.data.WordRepository
import com.wordiq.app.data.local.WordIqDatabase

class WordIqApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val database = WordIqDatabase.create(application)
    val words = WordRepository(database)
    val practice = PracticeRepository(database)
    val preferences = UserPreferencesRepository(application)
    val portability = DataPortabilityRepository(application, database)
}
