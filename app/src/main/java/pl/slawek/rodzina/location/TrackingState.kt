package pl.slawek.rodzina.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrackingStatus(
    val running: Boolean = false,
    val mode: TrackingMode = TrackingMode.PERIODIC,
    val remainingSeconds: Long = 0,
    val lastUploadAt: Long? = null,
    val message: String = "Udostępnianie wyłączone",
)

class TrackingState {
    private val mutable = MutableStateFlow(TrackingStatus())
    val status = mutable.asStateFlow()
    fun update(change: (TrackingStatus) -> TrackingStatus) = mutable.update(change)
}
