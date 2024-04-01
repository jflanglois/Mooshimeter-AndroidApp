package com.mooshim.mooshimeter

/**
 * Created by First on 2/3/2015.
 */
import android.app.Application
import android.content.Context
import android.util.Log
import com.mooshim.mooshimeter.common.Util
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject

val Context.applicationComponent get() = (applicationContext as MooshimeterApplication).component

@Component
abstract class ActivityComponent(@Component val parent: ApplicationComponent) {
    abstract val mainScreen: MainScreen
}

interface Analytics {
    fun log(message: String)
}

@Inject
class AnalyticsImpl : Analytics {
    override fun log(message: String) {
        Log.d("Meow" , message)
    }
}

class MooshimeterApplication : Application() {
    lateinit var component: ApplicationComponent

    override fun onCreate() {
        super.onCreate()
        component = ApplicationComponent::class.create()
        Util.init(this)
    }
}
