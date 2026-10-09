package pl.slawek.rodzina

import android.app.Application
import androidx.room.Room
import pl.slawek.rodzina.cloud.FamilyCloud
import pl.slawek.rodzina.data.LocationDatabase
import pl.slawek.rodzina.location.TrackingState

class FamilyLocatorApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, LocationDatabase::class.java, "locations.db").build()
    }
    val cloud by lazy { FamilyCloud() }
    val tracking = TrackingState()
}
