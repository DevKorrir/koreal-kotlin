package dev.korryr.koreal

import dev.korryr.koreal.data.repository.NetworkStatsRepository
import dev.korryr.koreal.data.repository.PacketRepository
import dev.korryr.koreal.ui.viewmodel.NetworkMonitorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { NetworkStatsRepository(get()) }
    single { PacketRepository() }
    viewModel { NetworkMonitorViewModel(get(), get()) }
}


