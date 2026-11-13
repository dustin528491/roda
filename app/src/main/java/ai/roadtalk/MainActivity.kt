package ai.roadtalk

import ai.roadtalk.ui.App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: AppModel by viewModels()

    /** Pleos 권한 중 위험 등급(FUSED_LOCATION 등)은 실행 중에 허락을 받아야 한다. 이미 허용된 것은 그냥 지나간다. */
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.car.refreshNavi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPermissions.launch(PERMISSIONS)
        setContent { App(vm) }
    }

    companion object {
        val PERMISSIONS = arrayOf(
            "pleos.car.permission.FUSED_LOCATION",
            "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.STT_SERVICE",
            "pleos.car.permission.TTS_SERVICE",
            "pleos.car.permission.CAR_WINDOWS",
            "pleos.car.permission.CAR_LIGHTS",
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.CAR_SEATS",
            "pleos.car.permission.CAR_CLIMATE",
            "pleos.car.permission.CAR_ENERGY",
            "pleos.car.permission.CAR_INFO",
        )
    }
}
