package com.ashrdev.aznd.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.body.BodyDao
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.domain.MeasurementSite
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bodyweight log and tape measurements. Lists are newest first; null until first read. */
class BodyViewModel(private val dao: BodyDao) : ViewModel() {

    val weights: StateFlow<List<BodyWeightEntry>?> =
        dao.observeWeights().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val measurements: StateFlow<List<BodyMeasurement>?> =
        dao.observeMeasurements().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun addWeight(kg: Double, atMs: Long) {
        viewModelScope.launch { dao.insertWeight(BodyWeightEntry(measuredAt = atMs, weightKg = kg)) }
    }

    fun deleteWeight(id: Long) {
        viewModelScope.launch { dao.deleteWeight(id) }
    }

    fun addMeasurement(site: MeasurementSite, cm: Double, atMs: Long) {
        viewModelScope.launch { dao.insertMeasurement(BodyMeasurement(site = site.name, measuredAt = atMs, valueCm = cm)) }
    }

    fun deleteMeasurement(id: Long) {
        viewModelScope.launch { dao.deleteMeasurement(id) }
    }

    companion object {
        fun factory(dao: BodyDao): ViewModelProvider.Factory =
            viewModelFactory { initializer { BodyViewModel(dao) } }
    }
}
