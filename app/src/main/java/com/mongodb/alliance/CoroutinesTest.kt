package com.mongodb.alliance

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

class CoroutinesTest : AppCompatActivity(), CoroutineScope {
    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coroutines_test)


        launch{
            val time = measureTimeMillis {
                val one = firstOperation()
                val two = secondOperation()
                Log.v(TAG(),"Cor. The answer is ${one + two}")
            }
            Log.v(TAG(),"Cor. Completed in $time ms")
        }

        Log.v(TAG(), "Cor. operation done")
    }

    suspend fun firstOperation() : Int {
        delay(1000)
        return 25
    }
    suspend fun secondOperation() : Int {
        delay(1000)
        return 21
    }
    suspend fun thirdOperation() : Int {
        delay(5000)
        return 10
    }
}