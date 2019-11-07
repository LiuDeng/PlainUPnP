package com.m3sv.plainupnp.presentation.main

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.m3sv.plainupnp.R

interface MainActivityNavigator {
    fun navigateToMain()
    fun navigateToSettings()
    fun isRoot(): Boolean
}

class MainActivityRouter(private val activity: AppCompatActivity) : MainActivityNavigator {
    override fun navigateToMain() {
        activity.findNavController(R.id.nav_host_container).popBackStack()
    }

    override fun navigateToSettings() {
        activity.findNavController(R.id.nav_host_container).navigate(R.id.settings_fragment)
    }

    override fun isRoot(): Boolean = with(activity.findNavController(R.id.nav_host_container)) {
        graph.startDestination == currentDestination?.id
    }
}
