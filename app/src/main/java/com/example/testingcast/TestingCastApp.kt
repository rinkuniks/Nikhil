package com.example.testingcast

import android.app.Application
import android.content.Context
import com.example.testingcast.di.AppContainer

class TestingCastApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as TestingCastApp).container
